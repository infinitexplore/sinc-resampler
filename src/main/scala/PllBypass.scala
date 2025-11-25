import chisel3._

class PllBypass extends PllIO {
  io.clko   := io.clki
  io.locked := true.B
}
