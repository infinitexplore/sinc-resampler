import chisel3._
import chisel3.util._

class SampleUartTx(val bitDepth: Int, val channels: Int, clockRate: Int)
    extends SampleWrite {
  val uartTx = IO(new Bundle {
    val txd     = Output(UInt(1.W))
    val running = Output(Bool())
  })

  dontTouch(uartTx.txd)

  val uart = Module(new UartTx(clockRate, 115200))
  uartTx.txd := uart.io.txd

  val reg = RegInit(VecInit(Seq.fill(channels)(0.S(bitDepth.W))))
  val vec = WireDefault(reg.asTypeOf(Vec(bitDepth / 8, UInt(8.W))))

  val cnt = RegInit(0.U(log2Ceil(bitDepth / 8).W))

  object State extends ChiselEnum {
    val sIdle, sTransmitting = Value
  }

  val state = RegInit(State.sIdle)
  io.sample.ready := state === State.sIdle

  uart.io.channel.valid := state === State.sTransmitting
  uart.io.channel.bits  := vec(cnt)

  uartTx.running := state === State.sTransmitting

  switch(state) {
    is(State.sIdle) {
      when(io.sample.valid) {
        cnt   := 0.U
        reg   := io.sample.bits
        state := State.sTransmitting
      }
    }
    is(State.sTransmitting) {
      when(uart.io.channel.fire) {
        when(cnt === (bitDepth / 8 - 1).U) {
          state := State.sIdle
        } otherwise {
          cnt := cnt + 1.U
        }
      }
    }
  }
}
