import chisel3._
import chisel3.util._

import chisel3.experimental.requireIsHardware

class NewSample(val channels: Int, params: RsmpParameters) extends Module {
  val io = IO(new Bundle {
    val inputSample = Flipped(DecoupledIO(Vec(channels, SInt(16.W))))
    val newSample   = DecoupledIO(Vec(channels, SInt(16.W)))
    val running     = Output(Bool())
  })

  requireIsHardware(params)

  val stepQuantErrAccReg = RegInit(0.U(16.W))
  val phaseStartReg      = RegInit(0.U(16.W))
  val phaseStepReg       = RegInit(
    0.S((params.hp.getWidth + params.stepFractional.getWidth + 1).W)
  )
  val inputShiftReg      = RegInit(params.initialInputShift)

  // mulAddVal goes through several CL components to generate the final sample.
  // Downstream module should always have input latch to get stable sample.
  // STA can detect timing violation.
  // However, a new state sPrepare is still added to gain one cycle margin.
  object State extends ChiselEnum {
    val sIdle, sInputShift, sCompute, sPrepare, sDone = Value
  }
  import State._

  val state   = RegInit(sIdle)
  val running = WireDefault(true.B)

  io.inputSample.ready := state === sInputShift
  io.newSample.valid   := state === sDone
  io.running           := running

  val sincInterplote = Module(new SincInterpolate)
  sincInterplote.io.phase := phaseStepReg.abs.asUInt

  val newSincValue = sincInterplote.io.newSincValue

  val mulAddVal = RegInit(VecInit(Seq.fill(channels)(0.S(32.W))))
  mulAddVal.zipWithIndex.foreach { case (sample, channel) =>
    val vSample = WireDefault(sample)
    val vShift  = vSample >> 2
    val vScale  = vShift * params.scale
    val vRound  = (vScale + (1 << (13 - 1)).S) >> 13
    val vResult = MuxCase(
      vRound,
      Seq(
        (vRound > 32767.S)  -> 32767.S,
        (vRound < -32768.S) -> -32768.S
      )
    )

    io.newSample.bits(channel) := vResult(15, 0).asSInt
  }

  // FIXME: use Mem instead of Reg to save resources
  val inputFIFOs = 0 until channels map { _ =>
    RegInit(VecInit(Seq.fill(RsmpParameters.filterMax)(0.S(16.W))))
  }

  // Input fifo size is for the maximum down-resampling factor.
  // When simulating in lower factors, not all elements are used.
  inputFIFOs.foreach { dontTouch(_) }

  val counter = RegInit(
    0.U((RsmpParameters.Bits.inputShift max RsmpParameters.Bits.filter).W)
  )

  switch(state) {
    is(sIdle) {
      counter := 1.U
      when(inputShiftReg === 0.U) {
        state        := sCompute
        phaseStepReg := (((params.hp * phaseStartReg) >> 15) + (RsmpParameters.numSamples << 7).U).zext
      }.otherwise {
        when(io.inputSample.valid) {
          state := sInputShift
        } otherwise {
          running := false.B
        }
      }
    }
    is(sInputShift) {
      when(io.inputSample.fire) {
        // FIXME: every register is equipped with one comparator, resource-inefficient.
        // When using Mem, read pointer and write pointer can be used to avoid this.
        inputFIFOs.zipWithIndex.foreach { case (fifo, channel) =>
          fifo.zipWithIndex.foldRight(0.S(16.W)) { case ((reg, index), acc) =>
            when(
              index.asUInt(
                RsmpParameters.Bits.filter.W
              ) === params.filterLength
            ) {
              reg := io.inputSample.bits(channel)
            } otherwise {
              reg := acc
            }
            reg
          }
        }
        counter := counter + 1.U
        when(counter === inputShiftReg) {
          counter      := 1.U
          state        := sCompute
          phaseStepReg := (((params.hp * phaseStartReg) >> 15) + (RsmpParameters.numSamples << 7).U).zext
        }
      }
    }
    is(sCompute) {
      counter      := counter + 1.U
      phaseStepReg := phaseStepReg - params.hp.zext
      inputFIFOs.zipWithIndex.foreach { case (fifo, channel) =>
        val sample        = fifo(counter)
        val mul           = sample * newSincValue
        val mulAfterRound = mul + (1 << (14 - 1)).S // rounding
        mulAddVal(channel) := mulAddVal(channel) + (mulAfterRound >> 14)
      }
      // FIXME: save synthesized resources by refining check condition.
      // For example, use sustraction of signed number and check the sign bit.
      when(counter === params.filterLength) {
        state := sPrepare
      }
    }
    is(sPrepare) {
      state := sDone
    }
    is(sDone) {
      when(io.newSample.ready) {
        val newPhase  = phaseStartReg + params.stepFractional
        val errAcc    = stepQuantErrAccReg + params.stepQuantErr
        val nextPhase = Wire(UInt((newPhase.getWidth + 1).W))
        when(errAcc === params.stepQuantErrAccMax) {
          when(params.stepQuantErrFix) {
            nextPhase := newPhase + 1.U
          } otherwise {
            nextPhase := newPhase - 1.U
          }
          stepQuantErrAccReg := errAcc - params.stepQuantErrAccMax
        } otherwise {
          nextPhase          := newPhase
          stepQuantErrAccReg := errAcc
        }

        val nextInputShift = params.stepIntegral
        when(nextPhase(15).asBool) {
          inputShiftReg := nextInputShift + 1.U
        } otherwise {
          inputShiftReg := nextInputShift
        }

        phaseStartReg := 0.B ## nextPhase(14, 0)
        mulAddVal.foreach { _ := 0.S }
        state         := sIdle
      }
    }
  }
}
