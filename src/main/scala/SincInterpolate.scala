import scala.io.Source

import chisel3._
import chisel3.util._

class SincInterpolate extends Module {
  val io = IO(new Bundle {
    val phase        = Input(UInt((RsmpParameters.Bits.sincTableIndex + 7).W))
    val newSincValue = Output(SInt(16.W))
  })

  val sampleOddFile   = "SincSamplesOdd.txt"
  val sampleEvenFile  = "SincSamplesEven.txt"
  val getLines        = Source.fromResource(_: String).getLines().map(_.toInt).toSeq
  val sincSamplesOdd  = VecInit(getLines(sampleOddFile).map(_.asSInt(16.W)))
  val sincSamplesEven = VecInit(getLines(sampleEvenFile).map(_.asSInt(16.W)))

  val phase = io.phase

  val thetaMI = Wire(SInt(8.W))
  thetaMI := (0.U ## phase(6, 0)).asSInt

  val ui = Wire(UInt(RsmpParameters.Bits.sincTableIndex.W))
  ui := phase(io.phase.getWidth - 1, 7)

  val isOdd       = ui(0).asBool
  val uiBy2       = ui >> 1
  val uiBy2Plus1  = uiBy2 + 1.U
  val sincSampleL = Mux(isOdd, sincSamplesOdd(uiBy2), sincSamplesEven(uiBy2))
  val sincSampleR =
    Mux(isOdd, sincSamplesEven(uiBy2Plus1), sincSamplesOdd(uiBy2))

  val sincD = sincSampleR - sincSampleL
  // FIXME: registering the output for pipelining to make possible higher frequency
  io.newSincValue := sincSampleL + ((sincD * thetaMI) >> 7.U)
}
