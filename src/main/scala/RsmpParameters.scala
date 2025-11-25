import chisel3._
import chisel3.util._
import chisel3.experimental.BundleLiterals._

class RsmpParameters extends Bundle {
  val hp                 = UInt(RsmpParameters.Bits.hp.W)
  val filterLength       = UInt(RsmpParameters.Bits.filter.W)
  val scale              = UInt(16.W)
  val stepIntegral       = UInt(log2Ceil(RsmpParameters.maxFactor).W)
  val stepFractional     = UInt(15.W)
  val stepQuantErrFix    = Bool() // true: +1, false: -1
  val stepQuantErr       = UInt(15.W)
  val stepQuantErrAccMax = UInt(
    RsmpParameters.Bits.stepQuantErrAccMax.W
  )
  val initialInputShift  = UInt(16.W)
  val inputLength        = UInt(16.W)
}

object RsmpParameters {
  val numCrossZeros     = 31
  val maxFactor         = 3   // 48K <-> 16K
  val numSamplesPerUnit = 256 // number of sinc samples in one unit
  val numSamples        = numSamplesPerUnit * numCrossZeros
  val filterMaxOneWing  = numCrossZeros * maxFactor
  val filterMax         =
    filterMaxOneWing * 2 + 1 // max filter length (one wing + one center + one wing)

  println(s"RsmpParameters.maxFactor = ${maxFactor}")
  println(s"RsmpParameters.filterMax = ${filterMax}")
  println(s"RsmpParameters.numSamples = ${numSamples}")

  object Bits {
    val hp                 = log2Ceil(numSamplesPerUnit * (1 << 7) + 1)
    val filter             = log2Ceil(filterMax)
    val inputShift         = log2Ceil(filterMaxOneWing)
    val stepQuantErrAccMax = log2Ceil(192000 + 1)
    val sincTableIndex     = log2Ceil(numSamples)

    println(s"RsmpParameters.Bits.hp = ${hp}")
    println(s"RsmpParameters.Bits.filter = ${filter}")
    println(s"RsmpParameters.Bits.inputShift = ${inputShift}")
    println(s"RsmpParameters.Bits.stepQuantErrAccMax = ${stepQuantErrAccMax}")
  }

  def apply(freqX: Int, freqY: Int): RsmpParameters = {
    require(freqX != freqY, "Input and output frequencies must be different")

    val gcd    = BigInt(freqX).gcd(BigInt(freqY)).toInt
    val ofreq  = freqY / gcd
    val ifreq  = freqX / gcd
    val factor = ofreq.toDouble / ifreq

    val hp = if (ofreq > ifreq) {
      numSamplesPerUnit * (1 << 7)
    } else {
      ((numSamplesPerUnit * factor) * (1 << 7)).round.toInt
    }

    val l = if (ofreq > ifreq) {
      numCrossZeros
    } else {
      (numCrossZeros * (1.0 / factor)).toInt
    }

    val scaleBase = (14746 * 0.95).toInt
    val scale     = if (ofreq > ifreq) {
      scaleBase
    } else {
      (scaleBase * factor).round.toInt
    }

    val factorInt  = ifreq / ofreq
    val d          = (ifreq - factorInt * ofreq) * (1 << 15)
    val factorFrac = (d.toDouble / ofreq).round.toInt

    val dq              = factorFrac * ofreq
    val stepQuantErr    = (dq - d).abs
    val stepQuantErrFix = if (dq > d) false else true

    (new RsmpParameters).Lit(
      _.hp                 -> hp.asUInt(Bits.hp.W),
      _.filterLength       -> (2 * l + 1).asUInt(Bits.filter.W),
      _.scale              -> scale.asUInt(16.W),
      _.stepIntegral       -> factorInt.asUInt(log2Ceil(maxFactor).W),
      _.stepFractional     -> factorFrac.asUInt(15.W),
      _.stepQuantErrFix    -> stepQuantErrFix.asBool,
      _.stepQuantErr       -> stepQuantErr.U(15.W),
      _.stepQuantErrAccMax -> ofreq.asUInt(Bits.stepQuantErrAccMax.W),
      _.initialInputShift  -> (l + 1).asUInt((Bits.inputShift).W)
    )
  }
}
