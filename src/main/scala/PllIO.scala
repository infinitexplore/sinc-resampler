import chisel3._

trait PllIO extends Module {
  val io = IO(new Bundle {
    val clki   = Input(Clock())
    val clko   = Output(Clock())
    val locked = Output(Bool())
  })
}
