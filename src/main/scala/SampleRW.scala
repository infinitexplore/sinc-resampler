import chisel3._
import chisel3.util._

trait SampleRWBase {
  require(bitDepth % 16 == 0, "bitDepth must be a multiple of 16")

  def channels: Int
  def bitDepth: Int

  trait SampleRWIf extends Bundle {
    val sample = DecoupledIO(Vec(channels, SInt(bitDepth.W)))
  }
}

trait SampleWrite extends Module with SampleRWBase {
  val io = IO(Flipped(new SampleRWIf {}))
}

trait SampleRead extends Module with SampleRWBase {
  val io = IO(new SampleRWIf {})
}
