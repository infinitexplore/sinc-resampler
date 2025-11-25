import scala.io.Source
import java.io.File

import chisel3._
import chisel3.util._
import chisel3.util.experimental.{loadMemoryFromFile, loadMemoryFromFileInline}

class SampleMemRead(
    val bitDepth:  Int,
    val channels:  Int,
    audioFilePath: String,
    numSamples:    Int,
    loadInline:    Boolean
) extends SampleRead {

  val addr = RegInit(0.U((log2Ceil(numSamples + 1).W)))

  dontTouch(io.sample)

  when(io.sample.fire) {
    addr := addr + 1.U
  }

  io.sample.valid := addr < numSamples.U

  val mem = Mem(numSamples, SInt((channels * 16).W))
  val v   = mem.read(addr)

  if (audioFilePath.trim().nonEmpty) {
    if (loadInline) {
      loadMemoryFromFileInline(mem, audioFilePath)
    } else {
      loadMemoryFromFile(mem, audioFilePath)
    }
  }

  io.sample.bits := VecInit(Seq.tabulate(channels) { i =>
    v((i + 1) * 16 - 1, i * 16).asSInt
  })
}
