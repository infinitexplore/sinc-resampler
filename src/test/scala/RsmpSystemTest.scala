import chisel3._
import chisel3.simulator.scalatest.ChiselSim
import org.scalatest.flatspec.AnyFlatSpec
import java.nio.file.Paths
import scala.io.Source

class RsmpSystemTest extends AnyFlatSpec with ChiselSim {
  val freqX         = 16000
  val freqY         = 48000
  val audioFileUrl  =
    getClass.getClassLoader
      .getResource("TestAudio.hex")
  val audioFilePath = Paths.get(audioFileUrl.toURI).toFile.getAbsolutePath
  val numSamples    = Source.fromFile(audioFilePath).getLines().size

  it should "run until finished" in {
    simulate(
      new RsmpSystem(freqX, freqY, 1, audioFilePath, numSamples, false)
    ) { dut =>
      dut.clock.step(20000)
      dut.io.finished.expect(true.B)
    }
  }

  it should "have correct number of start bits for each sample" in {
    simulate(
      new RsmpSystem(freqX, freqY, 1, audioFilePath, numSamples, false)
    ) { dut =>
      var numNewSamples = 0
      var numStartBits  = 0
      var preUartBit    = 1

      while (!dut.io.finished.peek().litToBoolean) {
        dut.clock.step()

        if (dut.io.newSampleFire.peek().litToBoolean) {
          numNewSamples += 1
        }

        val startBit = dut.io.txd.peek().litValue
        if (preUartBit == 1 && startBit == 0) {
          numStartBits += 1
        }
        preUartBit = startBit.toInt
      }
      // one sample has 16bits/2bytes, so 2 start bits per sample
      assert(numNewSamples * 2 == numStartBits)
    }
  }
}
