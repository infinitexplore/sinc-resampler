import chisel3._
import circt.stage.ChiselStage
import chisel3.util._

/**
 *  Modified from Leros UART implementation
 *  https://github.com/leros-dev/leros/blob/master/src/main/scala/leros/uart/UARTTx.scala
 */
class UartIO extends DecoupledIO(UInt(8.W))

class UartTx(val clockRate: Int, val baudRate: Int) extends Module {
  val io = IO(new Bundle {
    val txd     = Output(UInt(1.W))
    val channel = Flipped(new UartIO())
  })

  val clockDividerForBaudRate =
    ((clockRate + baudRate / 2) / baudRate - 1).asUInt
  val shiftReg                = RegInit(0x7ff.U)
  val cntReg                  = RegInit(0.U(22.W))
  val bitsReg                 = RegInit(0.U(4.W))

  io.channel.ready := bitsReg === 0.U
  io.txd           := Mux(bitsReg =/= 0.U, shiftReg(0), 1.U)

  when(cntReg === 0.U) {
    when(bitsReg =/= 0.U) {
      val shift = shiftReg >> 1
      shiftReg := 1.U ## shift(9, 0)
      bitsReg  := bitsReg - 1.U
    }
    cntReg := clockDividerForBaudRate
  } otherwise {
    cntReg := cntReg - 1.U
  }

  when(io.channel.fire) {
    shiftReg := 3.U ## io.channel.bits ## 0.U
    bitsReg  := 11.U
  }
}

class UartTxTop(clockRate: Int, baudRate: Int) extends Module {
  val io = IO(new Bundle {
    val txd = Output(UInt(1.W))
  })

  val pll = Module(new PllArtyA7100T)
  pll.io.clki := clock

  val resetUntilLocked = reset.asBool | !pll.io.locked

  withClockAndReset(pll.io.clko, resetUntilLocked) {
    val uartTx = Module(new UartTx(clockRate, baudRate))
    io.txd := uartTx.io.txd

    val alphabet    = "ABCDEFGHIJKLMNOPQRSTUVWXYZ\r\n".map(_.toByte)
    val alphabetVec = VecInit(alphabet.map(b => b.U(8.W)))

    val indexReg = RegInit(0.U(log2Ceil(alphabet.length).W))

    uartTx.io.channel.bits  := alphabetVec(indexReg)
    uartTx.io.channel.valid := true.B

    when(uartTx.io.channel.fire) {
      indexReg := Mux(
        indexReg === (alphabet.length - 1).U,
        0.U,
        indexReg + 1.U
      )
    }
  }
}

object UartTxTop extends App {
  ChiselStage.emitSystemVerilogFile(
    new UartTxTop(25000000, 115200),
    Array.empty,
    Array(
      "--strip-debug-info",
      "--disable-all-randomization",
      "--lower-memories",
      "--lowering-options=disallowLocalVariables,disallowPackedArrays"
    )
  )
}
