import chisel3._
import chisel3.util._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.flatspec.AnyFlatSpec

class SampleUartTxTestDriver extends Module {
  val io = IO(new Bundle {
    val txd = Output(UInt(1.W))
  })

  val tx = Module(new SampleUartTx(16, 1, 1000000))
  io.txd := tx.uartTx.txd

  val msg        = Seq(0x1234, 0x5678, 0x9abc, 0xdef0)
  val msgVec     = VecInit(msg.map(_.U(16.W).asSInt))
  val sampeIndex = RegInit(0.U(log2Ceil(msg.length).W))

  tx.io.sample.bits  := VecInit(msgVec(sampeIndex))
  tx.io.sample.valid := sampeIndex < msg.length.U

  when(tx.io.sample.fire) {
    sampeIndex := sampeIndex + 1.U
  }
}

class SampleUartTxTest extends AnyFlatSpec with ChiselSim {
  it should "transmit audio sample" in {
    simulate(new SampleUartTxTestDriver) { dut =>
      dut.clock.step(2000)
    }
  }

  it should "transmit a single sample correctly" in {
    val clockRate    = 1000000
    val baudRate     = 115200
    val cyclesPerBit = (clockRate + baudRate / 2) / baudRate

    simulate(new SampleUartTx(16, 1, clockRate)) { dut =>
      dut.io.sample.valid.poke(false.B)
      dut.clock.step(10)

      dut.io.sample.valid.poke(true.B)
      dut.io.sample.bits(0).poke(BigInt("1234", 16))
      dut.clock.step()
      dut.io.sample.valid.poke(false.B)

      def checkByte(byte: Int): Unit = {
        // wait for the start bit
        while (dut.uartTx.txd.peek().litValue == 1) {
          dut.clock.step()
        }
        // start
        assert(dut.uartTx.txd.peek().litValue == 0)
        dut.clock.step(cyclesPerBit)

        // data
        for (i <- 0 until 8) {
          val expectedBit = (byte >> i) & 1
          assert(dut.uartTx.txd.peek().litValue == expectedBit)
          dut.clock.step(cyclesPerBit)
        }

        // stop
        assert(dut.uartTx.txd.peek().litValue == 1)
        dut.clock.step(cyclesPerBit)
      }

      checkByte(0x34)
      checkByte(0x12)
    }
  }
}
