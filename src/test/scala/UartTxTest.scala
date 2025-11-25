import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.flatspec.AnyFlatSpec

class UartTxTest extends AnyFlatSpec with ChiselSim {
  it should "transmit a byte correctly" in {
    val clockRate    = 1000000
    val baudRate     = 100000
    val cyclesPerBit = clockRate / baudRate

    simulate(new UartTx(clockRate, baudRate)) { dut =>
      dut.io.channel.valid.poke(false.B)
      dut.clock.step(10)

      // Send a byte
      val testByte = 0x55.U
      dut.io.channel.valid.poke(true.B)
      dut.io.channel.bits.poke(testByte)
      dut.clock.step()
      dut.io.channel.valid.poke(false.B)

      // Wait for the transmission to start
      while (dut.io.txd.peek().litValue == 1) {
        dut.clock.step()
      }

      // Check start bit
      assert(dut.io.txd.peek().litValue == 0)
      dut.clock.step(cyclesPerBit)

      // Check data bits
      for (i <- 0 until 8) {
        val expectedBit = (testByte.litValue >> i) & 1
        assert(dut.io.txd.peek().litValue == expectedBit)
        dut.clock.step(cyclesPerBit)
      }

      // Check stop bit
      assert(dut.io.txd.peek().litValue == 1)
      dut.clock.step(cyclesPerBit)
    }
  }
}
