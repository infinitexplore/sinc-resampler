import chisel3._
import circt.stage.ChiselStage
import mainargs.{arg, main, Leftover, ParserForMethods}
import java.nio.file.Paths
import scala.io.Source

// FIXME: currently only supports 16-bits precision
class RsmpSystem(
    freqX:      Int,
    freqY:      Int,
    channels:   Int,
    audioFile:  String,
    numSamples: Int,
    loadInline: Boolean
) extends Module {
  val io        = IO(new Bundle {
    val finished      = Output(Bool())
    val newSampleFire = Output(Bool())
    val txd           = Output(UInt(1.W))
  })
  val params    = RsmpParameters(freqX, freqY)
  val newSample = Module(new NewSample(channels, params))

  val sampleRead =
    Module(new SampleMemRead(16, channels, audioFile, numSamples, loadInline))
  sampleRead.io.sample <> newSample.io.inputSample

  val sampleWrite = Module(new SampleUartTx(16, channels, 25000000))
  sampleWrite.io.sample <> newSample.io.newSample

  io.newSampleFire := newSample.io.newSample.fire
  io.txd           := sampleWrite.uartTx.txd

  io.finished := !sampleRead.io.sample.valid && !newSample.io.running && !sampleWrite.uartTx.running
}

class RsmpTop(
    freqX:     Int,
    freqY:     Int,
    channels:  Int,
    pllType:   String,
    audioFile: String
) extends Module {

  require(channels > 0 && channels <= 2, "Channels must be either 1 or 2")

  val numSamples = Source.fromResource(audioFile).getLines().size

  val io = IO(new Bundle {
    val finished      = Output(Bool())
    val newSampleFire = Output(Bool())
    val txd           = Output(UInt(1.W))
  })

  val pll = Module(if (pllType == "arty-a7-100t") {
    new PllArtyA7100T
  } else {
    new PllBypass
  })
  pll.io.clki := clock

  val resetUntilLocked = reset.asBool | !pll.io.locked
  val rsmp             =
    withClockAndReset(pll.io.clko, resetUntilLocked) {
      Module(
        new RsmpSystem(
          freqX,
          freqY,
          channels,
          audioFile,
          numSamples,
          true
        )
      )
    }
  io.finished      := rsmp.io.finished
  io.newSampleFire := rsmp.io.newSampleFire
  io.txd           := rsmp.io.txd
}

object RsmpTop extends App {
  @main
  def run(
      @arg(name = "freq-x", doc = "Input sample rate") freqX:         Int = 16000,
      @arg(name = "freq-y", doc = "Output sample rate") freqY:        Int = 48000,
      @arg(name = "channels", doc = "Number of channels") channels:   Int = 1,
      @arg(
        name = "pll-type",
        doc  = "Type of PLL to use (bypass or arty-a7-100t)"
      ) pllType:                                                      String = "arty-a7-100t",
      @arg(
        name = "audio-file-resource",
        doc  = "Audio file in resources to use"
      ) audioFile:                                                    String = "AudioPCM16KHz1ChSweep.mem",
      @arg(name = "chiselArgs", doc = "Chisel arguments") chiselArgs: Leftover[
        String
      ]
  ) = {
    val chiselArgsArray = chiselArgs.value.toArray

    println(s"Using audio file at: $audioFile")

    ChiselStage.emitSystemVerilogFile(
      new RsmpTop(freqX, freqY, channels, pllType, audioFile),
      chiselArgsArray,
      Array(
        "--strip-debug-info",
        "--disable-all-randomization",
        "--lower-memories",
        "--lowering-options=disallowLocalVariables,disallowPackedArrays"
      )
    )

    ChiselStage.emitCHIRRTLFile(
      new RsmpTop(freqX, freqY, channels, pllType, audioFile),
      chiselArgsArray
    )
  }

  ParserForMethods(this).runOrExit(args.toIndexedSeq)
}
