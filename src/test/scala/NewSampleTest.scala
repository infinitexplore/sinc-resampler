import chisel3._
import chisel3.util._
import chisel3.util.experimental.BoringUtils
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.flatspec.AnyFlatSpec

class NewSampleTestTop(channels: Int, rsmpParams: RsmpParameters)
    extends Module {
  val io = IO(new Bundle {
    val inputSample = Flipped(DecoupledIO(Vec(channels, SInt(16.W))))
    val newSample   = DecoupledIO(Vec(channels, SInt(16.W)))
  })

  val newSampleModule = Module(new NewSample(channels, rsmpParams))

  // val state = BoringUtils.bore(newSampleModule.state)
  // printf(p"State: $state\n")

  io.inputSample <> newSampleModule.io.inputSample
  io.newSample <> newSampleModule.io.newSample
}

class NewSampleTest extends AnyFlatSpec with ChiselSim {
  val freqX      = 16000
  val freqY      = 48000
  val rsmpParams = RsmpParameters(freqX, freqY)

  it should "go through state machine correctly" in {

    simulate(new NewSampleTestTop(1, rsmpParams)) { dut =>
      val initialInputShift =
        rsmpParams.initialInputShift.litValue.toInt
      val filterLength      = rsmpParams.filterLength.litValue.toInt

      dut.io.inputSample.valid.poke(false.B)
      dut.io.newSample.ready.poke(false.B)
      dut.clock.step()

      // sIdle
      dut.io.inputSample.ready.expect(false.B)
      dut.io.newSample.valid.expect(false.B)

      dut.io.inputSample.valid.poke(true.B)
      dut.io.inputSample.bits(0).poke(0.S)
      dut.clock.step()

      // sInputShift
      dut.io.inputSample.ready.expect(true.B)
      for (i <- 0 until initialInputShift) {
        dut.io.inputSample.bits(0).poke(i.S)
        dut.clock.step()
      }

      dut.clock.step()

      // sCompute
      dut.io.inputSample.valid.poke(false.B)
      dut.io.inputSample.ready.expect(false.B)
      for (i <- 0 until filterLength) {
        dut.clock.step()
      }

      dut.clock.step()

      // sDone
      dut.io.newSample.valid.expect(true.B)
      dut.io.newSample.ready.poke(true.B)
      dut.clock.step()

      // back to sIdle
      dut.io.newSample.valid.expect(false.B)
      dut.io.inputSample.ready.expect(false.B)
    }
  }

  it should "produce correct output samples for constant input" in {
    val constSample      = (0.5 * (1 << 15)).round.toInt.S(16.W)
    val inputSamples     = Seq.fill(64)(constSample)
    val outputSamplesRef = Seq(14791, 17199, 17440, 16316, 15031, 14502, 14884,
      15672, 16217, 16171, 15672, 15184, 15070, 15347, 15754, 15962, 15831,
      15518, 15287, 15296, 15518, 15754, 15809, 15674, 15475, 15366, 15429,
      15597, 15715, 15708, 15597)

    simulate(new NewSampleTestTop(1, rsmpParams)) { dut =>
      var outputIndex = 0

      dut.io.inputSample.valid.poke(true.B)
      for (sample <- inputSamples) {
        dut.clock.step()
        if (dut.io.inputSample.ready.peek().litToBoolean) {
          dut.io.inputSample.bits(0).poke(sample)
        } else {
          dut.io.newSample.ready.poke(true.B)
          def waitNewSample(maxCycles: Int): Unit = {
            if (maxCycles <= 0)
              return

            dut.clock.step()
            if (!dut.io.newSample.valid.peek().litToBoolean) {
              waitNewSample(maxCycles - 1)
            } else {
              val outSample = dut.io.newSample.bits(0).peek().litValue
              // println(s"${outputIndex}: Output sample: ${outSample}, ref: ${outputSamplesRef(outputIndex)}")
              if (outputIndex < outputSamplesRef.length) {
                assert(outSample == outputSamplesRef(outputIndex))
                outputIndex += 1
              }
              dut.io.newSample.ready.poke(false.B)
            }
          }
          waitNewSample(100)
        }
      }
    }
  }
}
