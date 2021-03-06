/*
 * Copyright 2019 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.acinq.eclair.wire

import fr.acinq.bitcoin.Block
import fr.acinq.eclair.{ShortChannelId, randomBytes32}
import org.scalatest.FunSuite
import scodec.bits._

/**
  * Created by PM on 31/05/2016.
  */

class FailureMessageCodecsSpec extends FunSuite {
  val channelUpdate = ChannelUpdate(
    signature = hex"3045022100c451cd65c88f55b1767941a247e849e12f5f4d4a93a07316659e22f5267d2088022009042a595c6bc8942cd9d729317b82b306edc259fb6b3a3cecb3dd1bd446e90601",
    chainHash = Block.RegtestGenesisBlock.hash,
    shortChannelId = ShortChannelId(12345),
    timestamp = 1234567L,
    cltvExpiryDelta = 100,
    messageFlags = 0,
    channelFlags = 1,
    htlcMinimumMsat = 1000,
    feeBaseMsat = 12,
    feeProportionalMillionths = 76,
    htlcMaximumMsat = None)

  test("encode/decode all channel messages") {
    val msgs: List[FailureMessage] =
      InvalidRealm :: TemporaryNodeFailure :: PermanentNodeFailure :: RequiredNodeFeatureMissing ::
        InvalidOnionVersion(randomBytes32) :: InvalidOnionHmac(randomBytes32) :: InvalidOnionKey(randomBytes32) ::
        TemporaryChannelFailure(channelUpdate) :: PermanentChannelFailure :: RequiredChannelFeatureMissing :: UnknownNextPeer ::
        AmountBelowMinimum(123456, channelUpdate) :: FeeInsufficient(546463, channelUpdate) :: IncorrectCltvExpiry(1211, channelUpdate) :: ExpiryTooSoon(channelUpdate) ::
        IncorrectOrUnknownPaymentDetails(123456L) :: IncorrectPaymentAmount :: FinalExpiryTooSoon :: FinalIncorrectCltvExpiry(1234) :: ChannelDisabled(0, 1, channelUpdate) :: ExpiryTooFar :: Nil

    msgs.foreach {
      case msg => {
        val encoded = FailureMessageCodecs.failureMessageCodec.encode(msg).require
        val decoded = FailureMessageCodecs.failureMessageCodec.decode(encoded).require
        assert(msg === decoded.value)
      }
    }
  }

  test("support encoding of channel_update with/without type in failure messages") {
    val tmp_channel_failure_notype = hex"10070080cc3e80149073ed487c76e48e9622bf980f78267b8a34a3f61921f2d8fce6063b08e74f34a073a13f2097337e4915bb4c001f3b5c4d81e9524ed575e1f45782196c98ed82555ef9e3d63483d921fa6c09c3f8e68caef8803585f23cf8ae75000008260500041300005b91b52f0003000e00000000000003e80000000100000001"
    val tmp_channel_failure_withtype = hex"100700820102cc3e80149073ed487c76e48e9622bf980f78267b8a34a3f61921f2d8fce6063b08e74f34a073a13f2097337e4915bb4c001f3b5c4d81e9524ed575e1f45782196c98ed82555ef9e3d63483d921fa6c09c3f8e68caef8803585f23cf8ae75000008260500041300005b91b52f0003000e00000000000003e80000000100000001"
    val ref = TemporaryChannelFailure(ChannelUpdate(hex"3045022100cc3e80149073ed487c76e48e9622bf980f78267b8a34a3f61921f2d8fce6063b022008e74f34a073a13f2097337e4915bb4c001f3b5c4d81e9524ed575e1f457821901", Block.LivenetGenesisBlock.hash, ShortChannelId(0x826050004130000L), 1536275759, 0, 3, 14, 1000, 1, 1, None))

    val u = FailureMessageCodecs.failureMessageCodec.decode(tmp_channel_failure_notype.toBitVector).require.value
    assert(u === ref)
    val bin = ByteVector(FailureMessageCodecs.failureMessageCodec.encode(u).require.toByteArray)
    assert(bin === tmp_channel_failure_withtype)
    val u2 = FailureMessageCodecs.failureMessageCodec.decode(bin.toBitVector).require.value
    assert(u2 === ref)
  }
}
