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

package fr.acinq.eclair.payment

import java.nio.ByteOrder

import fr.acinq.bitcoin.Crypto.{PrivateKey, PublicKey}
import fr.acinq.bitcoin.{Block, Btc, ByteVector32, Crypto, MilliBtc, MilliSatoshi, Protocol, Satoshi}
import fr.acinq.eclair.ShortChannelId
import fr.acinq.eclair.payment.PaymentRequest._
import org.scalatest.FunSuite
import scodec.DecodeResult
import scodec.bits._

/**
  * Created by fabrice on 15/05/17.
  */

class PaymentRequestSpec extends FunSuite {

  val priv = PrivateKey(hex"e126f68f7eafcc8b74f54d269fe206be715000f94dac067d1c04a8ca3b2db734", compressed = true)
  val pub = priv.publicKey
  val nodeId = pub
  assert(nodeId == PublicKey(hex"03e7156ae33b0a208d0744199163177e909e80176e55d97a2f221ede0f934dd9ad"))

  test("check minimal unit is used") {
    assert('p' === Amount.unit(MilliSatoshi(1)))
    assert('p' === Amount.unit(MilliSatoshi(99)))
    assert('n' === Amount.unit(MilliSatoshi(100)))
    assert('p' === Amount.unit(MilliSatoshi(101)))
    assert('n' === Amount.unit(Satoshi(1)))
    assert('u' === Amount.unit(Satoshi(100)))
    assert('n' === Amount.unit(Satoshi(101)))
    assert('u' === Amount.unit(Satoshi(1155400)))
    assert('m' === Amount.unit(MilliBtc(1)))
    assert('m' === Amount.unit(MilliBtc(10)))
    assert('m' === Amount.unit(Btc(1)))
  }

  test("check that we can still decode non-minimal amount encoding") {
    assert(Some(MilliSatoshi(100000000)) == Amount.decode("1000u"))
    assert(Some(MilliSatoshi(100000000)) == Amount.decode("1000000n"))
    assert(Some(MilliSatoshi(100000000)) == Amount.decode("1000000000p"))
  }

  test("data string -> bitvector") {
    import scodec.bits._
    assert(string2Bits("p") === bin"00001")
    assert(string2Bits("pz") === bin"0000100010")
  }

  test("minimal length long, left-padded to be multiple of 5") {
    import scodec.bits._
    assert(long2bits(0) == bin"")
    assert(long2bits(1) == bin"00001")
    assert(long2bits(42) == bin"0000101010")
    assert(long2bits(255) == bin"0011111111")
    assert(long2bits(256) == bin"0100000000")
    assert(long2bits(3600) == bin"000111000010000")
  }

  test("verify that padding is zero") {
    import scodec.bits._
    import scodec.codecs._
    val codec = PaymentRequest.Codecs.alignedBytesCodec(bits)

    assert(codec.decode(bin"1010101000").require == DecodeResult(bin"10101010", BitVector.empty))
    assert(codec.decode(bin"1010101001").isFailure) // non-zero padding

  }

  test("Please make a donation of any amount using payment_hash 0001020304050607080900010203040506070809000102030405060708090102 to me @03e7156ae33b0a208d0744199163177e909e80176e55d97a2f221ede0f934dd9ad") {
    val ref = "lnqc1pvjluezpp5qqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqypqdpl2pkx2ctnv5sxxmmwwd5kgetjypeh2ursdae8g6twvus8g6rfwvs8qun0dfjkxaqwsvexcd4hkjec6rnx4zk0xmtdte9fzzygjqxyn53lkr75zxtysgsmyc282fvmqhwppq6p5fljvvdy9jr3ykus0v9h9z53ufxthqznzsqpcyu97"
    val pr = PaymentRequest.read(ref)
    assert(pr.prefix == "lnqc")
    assert(pr.amount.isEmpty)
    assert(pr.paymentHash.bytes == hex"0001020304050607080900010203040506070809000102030405060708090102")
    assert(pr.timestamp == 1496314658L)
    assert(pr.nodeId == PublicKey(hex"03e7156ae33b0a208d0744199163177e909e80176e55d97a2f221ede0f934dd9ad"))
    assert(pr.description == Left("Please consider supporting this project"))
    assert(pr.fallbackAddress === None)
    assert(pr.tags.size === 2)
    assert(PaymentRequest.write(pr.sign(priv)) == ref)
  }

  test("Please send $3 for a cup of coffee to the same peer, within 1 minute") {
    val ref = "lnqc2500u1pvjluezpp5qqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqypqdq5xysxxatsyp3k7enxv4jsxqzpuxug2638gpkwdkfy53nhf73fu3t3ywzuugdq48w88vxxrcryr6s4q0nxax332374q0tjghylhfepa70np75ksztw5y3srrjxawufseuqp09cm5n"
    val pr = PaymentRequest.read(ref)
    assert(pr.prefix == "lnqc")
    assert(pr.amount == Some(MilliSatoshi(250000000L)))
    assert(pr.paymentHash.bytes == hex"0001020304050607080900010203040506070809000102030405060708090102")
    assert(pr.timestamp == 1496314658L)
    assert(pr.nodeId == PublicKey(hex"03e7156ae33b0a208d0744199163177e909e80176e55d97a2f221ede0f934dd9ad"))
    assert(pr.description == Left("1 cup coffee"))
    assert(pr.fallbackAddress === None)
    assert(pr.tags.size === 3)
    assert(PaymentRequest.write(pr.sign(priv)) == ref)
  }

  test("Now send $24 for an entire list of things (hashed)") {
    val ref = "lnqc20m1pvjluezpp5qqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqypqhp58yjmdan79s6qqdhdzgynm4zwqd5d7xmw5fk98klysy043l2ahrqsx68re6radj44t77x44tnp40ks035k7tmzzdeufjclajvel2gzhtqvvsq7wtgpc3yzanudmsheaaqjgmpyqs6kaudndl6lfak6pu042sqrf0nmj"
    // lnbc20m1pvjluezpp5qqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqypqhp58yjmdan79s6qqdhdzgynm4zwqd5d7xmw5fk98klysy043l2ahrqscc6gd6ql3jrc5yzme8v4ntcewwz5cnw92tz0pc8qcuufvq7khhr8wpald05e92xw006sq94mg8v2ndf4sefvf9sygkshp5zfem29trqq2yxxz7
    val pr = PaymentRequest.read(ref)
    assert(pr.prefix == "lnqc")
    assert(pr.amount == Some(MilliSatoshi(2000000000L)))
    assert(pr.paymentHash.bytes == hex"0001020304050607080900010203040506070809000102030405060708090102")
    assert(pr.timestamp == 1496314658L)
    assert(pr.nodeId == PublicKey(hex"03e7156ae33b0a208d0744199163177e909e80176e55d97a2f221ede0f934dd9ad"))
    assert(pr.description == Right(Crypto.sha256(ByteVector("One piece of chocolate cake, one icecream cone, one pickle, one slice of swiss cheese, one slice of salami, one lollypop, one piece of cherry pie, one sausage, one cupcake, and one slice of watermelon".getBytes))))
    assert(pr.fallbackAddress === None)
    assert(pr.tags.size === 2)
    assert(PaymentRequest.write(pr.sign(priv)) == ref)
  }

  test("The same, on testnet, with a fallback address qN4qgXGVFwtWH9kgfwQCYzS4H9k1mGouDK") {
    val ref = "lntq20m1pvjluezpp5qqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqypqhp58yjmdan79s6qqdhdzgynm4zwqd5d7xmw5fk98klysy043l2ahrqsfpp3x9et2e20v6pu37c5d9vax37wxq72un98tjz2dytv7shhw8hw0xn2pr0expfv3cqvutsgwy3hey260w6z3ed46wg6y3vwz2fl7acc2zejud4tja4p532r4ezgklrycrw33hfrmqsp43frj0"
    //     val ref = "lntb20m1pvjluezpp5qqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqypqhp58yjmdan79s6qqdhdzgynm4zwqd5d7xmw5fk98klysy043l2ahrqsfpp3x9et2e20v6pu37c5d9vax37wxq72un98k6vcx9fz94w0qf237cm2rqv9pmn5lnexfvf5579slr4zq3u8kmczecytdx0xg9rwzngp7e6guwqpqlhssu04sucpnz4axcv2dstmknqq6jsk2l"
    val pr = PaymentRequest.read(ref)
    assert(pr.prefix == "lntq")
    assert(pr.amount == Some(MilliSatoshi(2000000000L)))
    assert(pr.paymentHash.bytes == hex"0001020304050607080900010203040506070809000102030405060708090102")
    assert(pr.timestamp == 1496314658L)
    assert(pr.nodeId == PublicKey(hex"03e7156ae33b0a208d0744199163177e909e80176e55d97a2f221ede0f934dd9ad"))
    assert(pr.description == Right(Crypto.sha256(ByteVector.view("One piece of chocolate cake, one icecream cone, one pickle, one slice of swiss cheese, one slice of salami, one lollypop, one piece of cherry pie, one sausage, one cupcake, and one slice of watermelon".getBytes))))
    assert(pr.fallbackAddress === Some("qN4qgXGVFwtWH9kgfwQCYzS4H9k1mGouDK"))
    assert(pr.tags.size == 3)
    assert(PaymentRequest.write(pr.sign(priv)) == ref)
  }

  test("On mainnet, with fallback address QM2tzCG8hVgQ9CKZxZ8tPnhrqLxtSXou6y with extra routing info to go via nodes 029e03a901b85534ff1e92c43c74431f7ce72046060fcf7a95c37e148f78c77255 then 039e03a901b85534ff1e92c43c74431f7ce72046060fcf7a95c37e148f78c77255") {
    val ref = "lnqc20m1pvjluezpp5qqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqypqhp58yjmdan79s6qqdhdzgynm4zwqd5d7xmw5fk98klysy043l2ahrqsfpp3qjmp7lwpagxun9pygexvgpjdc4jdj85fr9yq20q82gphp2nflc7jtzrcazrra7wwgzxqc8u7754cdlpfrmccae92qgzqvzq2ps8pqqqqqqpqqqqq9qqqvpeuqafqxu92d8lr6fvg0r5gv0heeeqgcrqlnm6jhphu9y00rrhy4grqszsvpcgpy9qqqqqqgqqqqq7qqzqh6v6qvhssc5dtsr9cg6krupyfkazcvft55hp8yu5uuqra5ftlytksqyzw2y60k5fmvkreh66nq7rp0y9a80e2xn2tc6yf4q7xr4yekcphmav3n"
    // lnbc20m1pvjluezpp5qqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqypqhp58yjmdan79s6qqdhdzgynm4zwqd5d7xmw5fk98klysy043l2ahrqsfpp3qjmp7lwpagxun9pygexvgpjdc4jdj85fr9yq20q82gphp2nflc7jtzrcazrra7wwgzxqc8u7754cdlpfrmccae92qgzqvzq2ps8pqqqqqqpqqqqq9qqqvpeuqafqxu92d8lr6fvg0r5gv0heeeqgcrqlnm6jhphu9y00rrhy4grqszsvpcgpy9qqqqqqgqqqqq7qqzqj9n4evl6mr5aj9f58zp6fyjzup6ywn3x6sk8akg5v4tgn2q8g4fhx05wf6juaxu9760yp46454gpg5mtzgerlzezqcqvjnhjh8z3g2qqdhhwkj
    val pr = PaymentRequest.read(ref)
    assert(pr.prefix == "lnqc")
    assert(pr.amount === Some(MilliSatoshi(2000000000L)))
    assert(pr.paymentHash.bytes == hex"0001020304050607080900010203040506070809000102030405060708090102")
    assert(pr.timestamp == 1496314658L)
    assert(pr.nodeId == PublicKey(hex"03e7156ae33b0a208d0744199163177e909e80176e55d97a2f221ede0f934dd9ad"))
    assert(pr.description == Right(Crypto.sha256(ByteVector.view("One piece of chocolate cake, one icecream cone, one pickle, one slice of swiss cheese, one slice of salami, one lollypop, one piece of cherry pie, one sausage, one cupcake, and one slice of watermelon".getBytes))))
    assert(pr.fallbackAddress === Some("QM2tzCG8hVgQ9CKZxZ8tPnhrqLxtSXou6y"))
    assert(pr.routingInfo === List(List(
      ExtraHop(PublicKey(hex"029e03a901b85534ff1e92c43c74431f7ce72046060fcf7a95c37e148f78c77255"), ShortChannelId(72623859790382856L), 1, 20, 3),
      ExtraHop(PublicKey(hex"039e03a901b85534ff1e92c43c74431f7ce72046060fcf7a95c37e148f78c77255"), ShortChannelId(217304205466536202L), 2, 30, 4)
    )))
    assert(Protocol.writeUInt64(0x0102030405060708L, ByteOrder.BIG_ENDIAN) == hex"0102030405060708")
    assert(Protocol.writeUInt64(0x030405060708090aL, ByteOrder.BIG_ENDIAN) == hex"030405060708090a")
    assert(pr.tags.size == 4)
    assert(PaymentRequest.write(pr.sign(priv)) == ref)
  }


  test("On mainnet, with fallback (p2sh) address MLy36ApB4YZb2cBtTc1uYJhYsP2JkYokaf") {
    val ref = "lnqc20m1pvjluezpp5qqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqypqhp58yjmdan79s6qqdhdzgynm4zwqd5d7xmw5fk98klysy043l2ahrqsfppj3a24vwu6r8ejrss3axul8rxldph2q7z9y3a2lf8padjpnjw83urmvrlg3uluz9f2dsx2r752672knc8rt7a3a0mkrmunj8rkzch0tlmfwjsay8vue4ljyaym5jqj7ee95vsyl2gpt9jqv5"
    // lnbc20m1pvjluezpp5qqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqypqhp58yjmdan79s6qqdhdzgynm4zwqd5d7xmw5fk98klysy043l2ahrqsfppj3a24vwu6r8ejrss3axul8rxldph2q7z9kk822r8plup77n9yq5ep2dfpcydrjwzxs0la84v3tfw43t3vqhek7f05m6uf8lmfkjn7zv7enn76sq65d8u9lxav2pl6x3xnc2ww3lqpagnh0u
    val pr = PaymentRequest.read(ref)
    assert(pr.prefix == "lnqc")
    assert(pr.amount == Some(MilliSatoshi(2000000000L)))
    assert(pr.paymentHash.bytes == hex"0001020304050607080900010203040506070809000102030405060708090102")
    assert(pr.timestamp == 1496314658L)
    assert(pr.nodeId == PublicKey(hex"03e7156ae33b0a208d0744199163177e909e80176e55d97a2f221ede0f934dd9ad"))
    assert(pr.description == Right(Crypto.sha256(ByteVector.view("One piece of chocolate cake, one icecream cone, one pickle, one slice of swiss cheese, one slice of salami, one lollypop, one piece of cherry pie, one sausage, one cupcake, and one slice of watermelon".getBytes))))
    assert(pr.fallbackAddress === Some("MLy36ApB4YZb2cBtTc1uYJhYsP2JkYokaf"))
    assert(pr.tags.size == 3)
    assert(PaymentRequest.write(pr.sign(priv)) == ref)
  }

  test("On mainnet, with fallback (p2wpkh) address qc1qw508d6qejxtdg4y5r3zarvary0c5xw7kq52at0") {
    val ref = "lnqc20m1pvjluezpp5qqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqypqhp58yjmdan79s6qqdhdzgynm4zwqd5d7xmw5fk98klysy043l2ahrqsfppqw508d6qejxtdg4y5r3zarvary0c5xw7kxk9h0ww00jgpsux47wkshdczrz8exrtf2urfdne8u9kydtpc5p4q309f2ymqjam3r5tmy6jtnek6vgv4tqeqazuf7vylg3appapp03gqlma2eq"
    // lnbc20m1pvjluezpp5qqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqypqhp58yjmdan79s6qqdhdzgynm4zwqd5d7xmw5fk98klysy043l2ahrqsfppqw508d6qejxtdg4y5r3zarvary0c5xw7kknt6zz5vxa8yh8jrnlkl63dah48yh6eupakk87fjdcnwqfcyt7snnpuz7vp83txauq4c60sys3xyucesxjf46yqnpplj0saq36a554cp9wt865
    val pr = PaymentRequest.read(ref)
    assert(pr.prefix == "lnqc")
    assert(pr.amount == Some(MilliSatoshi(2000000000L)))
    assert(pr.paymentHash.bytes == hex"0001020304050607080900010203040506070809000102030405060708090102")
    assert(pr.timestamp == 1496314658L)
    assert(pr.nodeId == PublicKey(hex"03e7156ae33b0a208d0744199163177e909e80176e55d97a2f221ede0f934dd9ad"))
    assert(pr.description == Right(Crypto.sha256(ByteVector.view("One piece of chocolate cake, one icecream cone, one pickle, one slice of swiss cheese, one slice of salami, one lollypop, one piece of cherry pie, one sausage, one cupcake, and one slice of watermelon".getBytes))))
    assert(pr.fallbackAddress === Some("qc1qw508d6qejxtdg4y5r3zarvary0c5xw7kq52at0"))
    assert(pr.tags.size == 3)
    assert(PaymentRequest.write(pr.sign(priv)) == ref)
  }


  test("On mainnet, with fallback (p2wsh) address qc1qrp33g0q5c5txsp9arysrx4k6zdkfs4nce4xj0gdcccefvpysxf3qtd3g7a") {
    val ref = "lnqc20m1pvjluezpp5qqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqypqhp58yjmdan79s6qqdhdzgynm4zwqd5d7xmw5fk98klysy043l2ahrqsfp4qrp33g0q5c5txsp9arysrx4k6zdkfs4nce4xj0gdcccefvpysxf3qsj465jnssrfn56q2fthq3szuzrl07x5m9jtdw0u5er2wyqqjqvxsa5cezhsqecp8wdrpvkmvk0husz2wjqtzrmhvltf3uyq39m58vdqplxc5fr"
    // lnbc20m1pvjluezpp5qqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqypqhp58yjmdan79s6qqdhdzgynm4zwqd5d7xmw5fk98klysy043l2ahrqsfp4qrp33g0q5c5txsp9arysrx4k6zdkfs4nce4xj0gdcccefvpysxf3qvnjha2auylmwrltv2pkp2t22uy8ura2xsdwhq5nm7s574xva47djmnj2xeycsu7u5v8929mvuux43j0cqhhf32wfyn2th0sv4t9x55sppz5we8
    val pr = PaymentRequest.read(ref)
    assert(pr.prefix == "lnqc")
    assert(pr.amount == Some(MilliSatoshi(2000000000L)))
    assert(pr.paymentHash.bytes == hex"0001020304050607080900010203040506070809000102030405060708090102")
    assert(pr.timestamp == 1496314658L)
    assert(pr.nodeId == PublicKey(hex"03e7156ae33b0a208d0744199163177e909e80176e55d97a2f221ede0f934dd9ad"))
    assert(pr.description == Right(Crypto.sha256(ByteVector.view("One piece of chocolate cake, one icecream cone, one pickle, one slice of swiss cheese, one slice of salami, one lollypop, one piece of cherry pie, one sausage, one cupcake, and one slice of watermelon".getBytes))))
    assert(pr.fallbackAddress === Some("qc1qrp33g0q5c5txsp9arysrx4k6zdkfs4nce4xj0gdcccefvpysxf3qtd3g7a"))
    assert(pr.tags.size == 3)
    assert(PaymentRequest.write(pr.sign(priv)) == ref)
  }

  test("On mainnet, with fallback (p2wsh) address qc1qrp33g0q5c5txsp9arysrx4k6zdkfs4nce4xj0gdcccefvpysxf3qtd3g7a and a minimum htlc cltv expiry of 12") {
    val ref = "lnqc20m1pvjluezpp5qqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqypqcqpvhp58yjmdan79s6qqdhdzgynm4zwqd5d7xmw5fk98klysy043l2ahrqsfp4qrp33g0q5c5txsp9arysrx4k6zdkfs4nce4xj0gdcccefvpysxf3qqkk8g9e86epmefgvsdsf0vq9u4qey2un9fpcm49yfdcrv8avcxtpc9he69s893xtk5ggnps4yjr76gvu85xffhu3fdxmscnxe0nttqcqvem9t6"
    // lnbc20m1pvjluezcqpvpp5qqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqqqsyqcyq5rqwzqfqypqhp58yjmdan79s6qqdhdzgynm4zwqd5d7xmw5fk98klysy043l2ahrqsfp4qrp33g0q5c5txsp9arysrx4k6zdkfs4nce4xj0gdcccefvpysxf3q90qkf3gd7fcqs0ewr7t3xf72ptmc4n38evg0xhy4p64nlg7hgrmq6g997tkrvezs8afs0x0y8v4vs8thwsk6knkvdfvfa7wmhhpcsxcqw0ny48
    val pr = PaymentRequest.read(ref)
    assert(pr.prefix == "lnqc")
    assert(pr.amount == Some(MilliSatoshi(2000000000L)))
    assert(pr.paymentHash.bytes == hex"0001020304050607080900010203040506070809000102030405060708090102")
    assert(pr.timestamp == 1496314658L)
    assert(pr.nodeId == PublicKey(hex"03e7156ae33b0a208d0744199163177e909e80176e55d97a2f221ede0f934dd9ad"))
    assert(pr.description == Right(Crypto.sha256(ByteVector.view("One piece of chocolate cake, one icecream cone, one pickle, one slice of swiss cheese, one slice of salami, one lollypop, one piece of cherry pie, one sausage, one cupcake, and one slice of watermelon".getBytes))))
    assert(pr.fallbackAddress === Some("qc1qrp33g0q5c5txsp9arysrx4k6zdkfs4nce4xj0gdcccefvpysxf3qtd3g7a"))
    assert(pr.minFinalCltvExpiry === Some(12))
    assert(pr.tags.size == 4)
    assert(PaymentRequest.write(pr.sign(priv)) == ref)
  }

  test("correctly serialize/deserialize variable-length tagged fields") {
    val number = 123456

    val codec = PaymentRequest.Codecs.dataCodec(scodec.codecs.bits).as[PaymentRequest.Expiry]
    val field = PaymentRequest.Expiry(number)

    assert(field.toLong == number)

    val serializedExpiry = codec.encode(field).require
    val field1 = codec.decodeValue(serializedExpiry).require
    assert(field1 == field)

    // Now with a payment request
    val pr = PaymentRequest(chainHash = Block.LivenetGenesisBlock.hash, amount = Some(MilliSatoshi(123)), paymentHash = ByteVector32(ByteVector.fill(32)(1)), privateKey = priv, description = "Some invoice", expirySeconds = Some(123456), timestamp = 12345)

    val serialized = PaymentRequest.write(pr)
    val pr1 = PaymentRequest.read(serialized)
    assert(pr == pr1)
  }

  test("ignore unknown tags") {
    val pr = PaymentRequest(
      prefix = "lntq",
      amount = Some(MilliSatoshi(100000L)),
      timestamp = System.currentTimeMillis() / 1000L,
      nodeId = nodeId,
      tags = List(
        PaymentHash(ByteVector32(ByteVector.fill(32)(1))),
        Description("description"),
        UnknownTag21(BitVector("some data we don't understand".getBytes))
      ),
      signature = ByteVector.empty).sign(priv)

    val serialized = PaymentRequest write pr
    val pr1 = PaymentRequest read serialized
    val Some(unknownTag) = pr1.tags.collectFirst { case u: UnknownTag21 => u }
  }

  test("accept uppercase payment request") {
    val input = "lnqc1500n1pwxx94fpp5q3xzmwuvxpkyhz6pvg3fcfxz0259kgh367qazj62af9rs0pw07dsdpa2fjkzep6yp58garswvaz7tmvd9nksarwd9hxw6n0w4kx2tnrdakj7grfwvs8wxqr23sfv37dy2swqwsnlggztre8vcqgaevzk6vfjl03344rzkpchwhg95x4axg5k7cfr4w82csjfvu59ecjumwn5antm0h6el0ah04le0g8gcplyyuya"

    assert(PaymentRequest.write(PaymentRequest.read(input.toUpperCase())) == input)
  }

  test("nonreg") {
    val requests = List(
      "lnqc40n1pw9qjvwpp5qq3w2ln6krepcslqszkrsfzwy49y0407hvks30ec6pu9s07jur3sdpstfshq5n9v9jzucm0d5s8vmm5v5s8qmmnwssyj3p6yqenwdenxqrrss480cqve00x3ur8mmjhdrn3gt6sa6tvaarsce84jkygexyrm9gn7qarznmttak6ckp3ue7af267pd649ek80sg29jnaqzdc2ryzzqpdgpr9r3q9",
      "lnqc1500n1pwyvqwfpp5p5nxwpuk02nd2xtzwex97gtjlpdv0lxj5z08vdd0hes7a0h437qsdpa2fjkzep6yp8kumrfdejjqempd43xc6twvusxjueqd9kxcet8v9kzqct8v95kuxqr23suf3rj0lzdsm7qgc59lsnl6qez25d6qczgv6k8gs3gkre5ed7rv5hygmawnr57w4vvfngq8slgdzwj53ecvr25mzm7q65lt52v2nfqeqq8ecy3f",
      "lnqc800n1pwykdmfpp5zqjae54l4ecmvm9v338vw2n07q2ehywvy4pvay53s7068t8yjvhqdqddpjkcmr0yysjza99rgdk26ge47pzchrsv7yycaec4ar2s8zydude7wdg87wf5ynasqzn64c4e366qxpl5nrj0z5ze8hes5jecpuur5zd5agdp4vpptxcqcp4ps4",
      "lnqc300n1pwzezrnpp5zgwqadf4zjygmhf3xms8m4dd8f4mdq26unr5mfxuyzgqcgc049tqdq9dpjhjxuxgszh7hw5fe39h2uapljykj0ghfwlet6xv7w7xc8td75400vhjpp5hhcmgkaf0tynsxnvdq5smppdhhm9we6w5kt4pj2cpjvsx0kgqg5amwa",
      "lnqc10n1pdm2qaxpp5zlyxcc5dypurzyjamt6kk6a8rpad7je5r4w8fj79u6fktnqu085sdpl2pshjmt9de6zqen0wgsrzgrsd9ux2mrnypshggrnv96x7umgd9ejuurvv93k2tsxqzjccefp7yqwc40gprmzu48c5tc39rk2nkt6guj7t5cw0uazzrhs8w84jsfpwj9y77c45pyj0n4dxwzm3t52u99psyneswrhuxmyuk584tgpqh0kwj",
      "lnqc800n1pwp5uuhpp5y8aarm9j9x9cer0gah9ymfkcqq4j4rn3zr7y9xhsgar5pmaceaqqdqdvf5hgcm0d9hzzhg7qwdtkcwg67m9fshwqkc6094vpu9jmrqlhtna5ffvzzw7zyf5hzr4jyzmp8mka5ahznw6n2ck3k2j4tq67m8wzdxqq5u2mdvwfq8gp74kclz",
      "lnqc1500n1pdl686hpp5y7mz3lgvrfccqnk9es6trumjgqdpjwcecycpkdggnx7h6cuup90sdpa2fjkzep6ypqkymm4wssycnjzf9rjqurjda4x2cm5ypskuepqv93x7at5ypek7xqr23sqca8a3cymztzflsfd4xj88g9g4pfr6j438c88myvcg6h528r9hgh7ehjl5xyx7eu50v9nl09tlzsgfsteqsrqlvz0lad7szuhr56ftgpwkh9ya",
      "lnqc80n1pwykw99pp5965lyj4uesussdrk0lfyd2qss9m23yjdjkpmhw0975zky2xlhdtsdpl2pshjmt9de6zqen0wgsrsgrsd9ux2mrnypshggrnv96x7umgd9ejuurvv93k2tsxqzjcftyf79sfefqv992s4v2qgfdvh7kpt6f27hwshk4yxkd5pc8nqrkhfrnap9wyhsh4s9rn5yjkg0tjvza4rpwd24ljcj44ktwf3x8khmsp3y3m33",
      "lnqc2200n1pwp4pwnpp5xy5f5kl83ytwuz0sgyypmqaqtjs68s3hrwgnnt445tqv7stu5kyqdpyvf5hgcm0d9hzqmn0wssxymr0vd4kx6rpd9hq2x4y88z53fu4j55jarphce4md5ae2muemjx5qkalpuu20avp54dqn2mn7nmnkecavg8xx3hz46xayrrtpnxgjxvnmukcyveyuq0qe6sqng4qks",
      "lnqc300n1pwp50ggpp5x7x5a9zs26amr7rqngp2sjee2c3qc80ztvex00zxn7xkuzuhjkxqdq9dpjhjq577lp5gmlsne9ygl8w90jjr4c6h0qf827st86u0rve327n2msxnp7wakd6rslxadmlngu62fdsh67fc5jcupl7405xynhpngrcsgvcq5qfemm",
      "lnqc10n1pd6jt93pp58vtzf4gup4vvqyknfakvh59avaek22hd0026snvpdnc846ypqrdsdp0tfshq5n9v9jzucm0d5s8vmm5v5s8qmmnwssyj3p6yqcnqvsxqyd9uqnknayt0zhm79fxurnmxx382v07flvp9ngvmyze8det0szg0xj3k4ypnnkz33eus8n2tms2clr56m7kz8kh9v6flafphg60r4vpa4kngpp4p379",
      "lnqc890n1pwzu4uqpp5gy274lq0m5hzxuxy90vf65wchdszrazz9zxjdk30ed05kyjvwxrqdzq2pshjmt9de6zqen0wgsrswfqwp5hsetvwvsxzapqwdshgmmndp5hxtnsd3skxefwxqzjckk52xgsvfefpkkq800ta0ch7unr7h2zzspgxh92657me7cteppxxlp9d9jm2s0s0vduslv4qckmrs84tq239sw05ae5yk5rkae8pujcp785xkg",
      "lnqc79760n1pd7cwyapp5gevl4mv968fs4le3tytzhr9r8tdk8cu3q7kfx348ut7xyntvnvmsdz92pskjepqw3hjqmrfva58gmnfdenjqumvda6zqmtpvd5xjmn9ypnx7u3qx5czq5msd9h8xxqrrss56n636r924hddzn4w4s05qjqpmum2cv06kz5dqf7frrdz7t6l77970ggggw9shn50hwqzx735pryqcn46t5ye0wkym34z7v8085qfmqqtfqpgy",
      "lnqc90n1pduns5qpp5f5h5ghga4cp7uj9de35ksk00a2ed9jf774zy7va37k5zet5cds8sdpl2pshjmt9de6zqen0wgsrjgrsd9ux2mrnypshggrnv96x7umgd9ejuurvv93k2tsxqzjc2ph6ecuqdlm20tjdjquchxdnfdw695cvzjdr8smrfjphu5akn4k5ma57dk9d9xqegxkr6r9aed2ar7vymrua5ks7z8l2xz4mhu4pjusqm97rme",
      "lnqc10u1pw9nehppp5tf0cpc3nx3wpk6j2n9teqwd8kuvryh69hv65w7p5u9cqhse3nmgsdzz2p6hycmgv9ek2gr0vcsrzgrxd3hhwetjyphkugzzd96xxmmfdcsywunpwejhjctjvstvzvhn4uwhvx659tvqlucdt8ce38kvdwfher48ffr3g3y2k80wa5gn32sgjumtftq3wt00crze83ca39ukfx5w7rrhuvgdrlyxy4r3sp349swt",
      "lnqc30n1pw9qjwmpp5tcdc9wcr0avr5q96jlez09eax7djwmc475d5cylezsd652zvptjsdpstfshq5n9v9jzucm0d5s8vmm5v5s8qmmnwssyj3p6yqenwdf4xqrrssj65pr9dnqrfjr6cwp3xsuthk5r5euq55umnj4gjrs5p06v34zvxq6sfzgfneusc7593un9aqe6gmw65jsx6jzfymct2t6j9feq6shcsqxqjcus",
      "lnqc10u1pw9x36xpp5tlk00k0dftfx9vh40mtdlu844c9v65ad0kslnrvyuzfxqhdur46qdzz2p6hycmgv9ek2gr0vcsrzgrxd3hhwetjyphkugzzd96xxmmfdcsywunpwejhjctjvsznvejfx5vf3nfclkw99tht9a3x0wvwjt8jfs5rzxuahctpg64m7yjwljfmqqtqcgk7zq62037ufgfup4xsxam6m8w6hppaqgsscpmwgqkxvzrv",
      "lnqc40n1pd6jttkpp5v8p97ezd3uz4ruw4w8w0gt4yr3ajtrmaeqe23ttxvpuh0cy79axqdp0tfshq5n9v9jzucm0d5s8vmm5v5s8qmmnwssyj3p6yqcnqvsxqyd9uqaq4vahp2la2rrhzmzdsnjmgcna0mpfxqcx3sf8pqghlg4gqswg93lr47jt83lw7uqmwwqnrrhap83fcddaq4jnuarzq0nt9s39pdmjspuhlafq",
      "lnqc1pwr7fqhpp5vhur3ahtumqz5mkramxr22597gaa9rnrjch8gxwr9h7r56umsjpqdpl235hqurfdcs9xct5daeks6tngask6etnyq58g6tswp5kutndv55jsaf3x5unj2gxqyz5vqsfp2ludzqq7xzv9fkz03x2282r2px3xwztzg4vf6hhep9ruh5mr5rglxplvnvsg7f6qy2naazfrr5v02t0ujf2jrt753a0rhcrypensq9t0fzy",
      "lnqc10n1pw9rt5hpp5dsv5ux7xlmhmrpqnffgj6nf03mvx5zpns3578k2c5my3znnhz0gqdpstfshq5n9v9jzucm0d5s8vmm5v5s8qmmnwssyj3p6yqenwwp3xqrrsscyy9hdzu6762uwekplpx3nxdu3lr8ek0vxc4vuh5c5ewmp0yh288glqmzr8u5ks50jyuux9fpjpf2r9cacvp7vgp94rlkvccnlv3lgqqqt0q3h",
      "lnqc1500n1pd7u7p4pp5d54vffcehkcy79gm0fkqrthh3y576jy9flzpy9rf6syua0s5p0jqdpa2fjkzep6ypxhjgz90pcx2unfv4hxxefqdanzqargv5s9xetrdahxggzvd9nksxqr23skhd4nf23asnkl8v9tvx7r9l939a06dq9w2d9d8t0dvsk8ys8l4xypzderqfvmxm4juwpt5l4y4pqjkz76ws34j30nkpvn57jw29waqgqxqva3t",
      "lnqc10n1pdunsmgpp5wn90mffjvkd06pe84lpa6e370024wwv7xfw0tdxlt6qq8hc7d7rqdp0tfshq5n9v9jzucm0d5s8vmm5v5s8qmmnwssyj3p6yqcngvsxqyd9uqp035gpya2z6d5tuye6yffju4647evh6je8xywagu2tvjxkc5prps2qulxyppfcpdkadvss2yvznhenr49q7knmunvl5h0v3r60acdvcq8wypgp",
      "lnqc700n1pwp50wapp5w7eearwr7qjhz5vk5zq4g0t75f90mrekwnw4e795qfjxyaq27dxsdqvdp6kuar9wgeqxhqwxtz42m0l4mc58t2czez6kf0tjw7q38nl6m8zkcr2nj9lr4prns200m59k6gdk76vlncwdzs4cydp3teapla6hsmejzya8szy4vcq45n5ye",
      "lnqc10n1pd6jvy5pp50x9lymptter9najcdpgrcnqn34wq34f49vmnllc57ezyvtlg8ayqdpdtfshq5n9v9jzucm0d5s8vmm5v5s8qmmnwssyj3p6yq6rvxqyd9uqnmljmvm8x0g6wvyx3m6ljlymd0398t96unqwxyw7cxt0gg0h7zdpf7xs3l8ffl2sasp6g90zq5fq4l58fezunvm9v3cduh9m7rjmmkgqtfvzu9",
      "lnqc10n1pw9pqz7pp50782e2u9s25gqacx7mvnuhg3xxwumum89dymdq3vlsrsmaeeqsxsdpstfshq5n9v9jzucm0d5s8vmm5v5s8qmmnwssyj3p6yqenwd3cxqrrssgpm7e84w7t2h84rq44glwehnjyt6p957wh565nsvlv7m8fpnws88jwgqmgpzqjvnf3kwwd8mu9vvm2c7htqlf2r2f8z7d8wywhkxhgsqm3n0rl",
      "lnqc1300n1pwq4fx7pp5sqmq97yfxhhk7xv7u8cuc8jgv5drse45f5pmtx6f5ng2cqm332uqdq4e2279q9zux62tc5q5t9fgy4au7ygfcud5zyn0eaffukmanxhxu8s4kytdywxxhr3le7q3ggy3n0gd9cnsxssrj8efcqzr2sydg29gmyua9f6w645u2r0zegn20acqhl42rl",
      "lnqc1u1pd7u7tnpp5s9he3ccpsmfdkzrsjns7p3wpz7veen6xxwxdca3khwqyh2ezk8kqdqdg9jxgg8sn7f27xqr23sc3aq6jg57slqjgc93ezxjvaxyxj85wzcendqzpqmdyshqe2qa5nngyzzfrvvtgtksdxtn7m7zurx27xats3fz9xj3a7fqqzg6ker9nsp8xe0mf",
      "lnqc1200n1pwq5kf2pp5snkm9kr0slgzfc806k4c8q93d4y57q3lz745v2hefx952rhuymrqdq509shjgrzd96xxmmfdcssyhz29p8r9rakrvnkd60cst92ddh52lwdr5syj7ys3mjxe80v9ds5ve8fk57qkrdd0w38kqaq3ljd4j824ulqmqwzfh3r8v82nhdgwzqq0hgk3s",
      "lnqc1500n1pd7u7v0pp5s6d0wqexag3aqzugaw3gs7hw7a2wrq6l8fh9s42ndqu8zu480m0sdqvg9jxgg8zn2ssxqr23sqxc4gn3qnan00ed4j4hfm64t39aqqptwwn37fhlqmxq297yy3yv8uauujder8xnptkvtp7g3lydnx8fqr2wpn8l60p3cuze5yx5lrjqpmlswsw",
      "lnqc100n1pd6jv8ypp53p6fdd954h3ffmyj6av4nzcnwfuyvn9rrsc2u6y22xnfs0l0cssqdpdtfshq5n9v9jzucm0d5s8vmm5v5s8qmmnwssyj3p6yqersxqyd9uqjefrq533t7w52yctc554jtk047vad47jq6pyy6qfpdqsfggh4gehf2s6u9j99p7gtp5r2pwpfcqt6zzs9zrevlek2p64f2alkmyxfwspqawhyz",
      "lnqc2300n1pwp50w8pp53030gw8rsqac6f3sqqa9exxwfvphsl4v4w484eynspwgv5v6vyrsdp9w35xjueqd9ejqmn0wssx67fqwpshxumhdaexgjnvpej0ehwky3vsxqhpakryhxtp90e0aelesj46wp8xmv2zzzm9rns2aqz3xx36zpdd77m2zc5dghxmmrvmjrxu0tn7ymjxjj39n2tcpeme9ey",
      "lnqc10n1pwykdlhpp53392ama65h3lnc4w55yqycp9v2ackexugl0ahz4jyc7fqtyuk85qdpstfshq5n9v9jzucm0d5s8vmm5v5s8qmmnwssyj3p6yqenwvejxqrrsswujerzwkf2twm68kcenkzep0kywarwazm6mq23t63cugvtkyawkrr4w2z8vq4hmnarxl7whw46l5e7c9gkesry30x0vuegzd78xhpccpmhkmet",
      "lnqc10470n1pw9qf40pp535pels2faqwau2rmqkgzn0rgtsu9u6qaxe5y6ttgjx5qm4pg0kgsdzy2pshjmt9de6zqen0wgsrzvp5xus8q6tcv4k8xgrpwss8xct5daeks6tn9ecxcctrv5hqxqzjc7vleamj2n5drrhuqpkxqr4g7lvq9zgpht5r607kxcwymu69re38xrgpcczdg534fuzj4f225uqztlzceymmc87ccs2yr4dsnj46fzxcqql2mnj",
      "lnqc100n1pwytlgspp5365rx7ell807x5jsr7ykf2k7p5z77qvxjx8x6pfhh5298xnr6d2sdpstfshq5n9v9jzucm0d5s8vmm5v5s8qmmnwssyj3p6yqenwvpsxqrrssxyldj3pwh4uv50ptswltrta2d3jfffvk3nd6u9qum5p4m2smwzzhckskv55khaf6s9n9kkxrzx7wjwgcj8ctg68r3utxcp8cenagp2gqmjq2as",
      "lnqc210n1pdunsefpp5jxn3hlj86evlwgsz5d70hquy78k28ahdwjmlagx6qly9x29pu4uqdzq2pshjmt9de6zqen0wgsryvfqwp5hsetvwvsxzapqwdshgmmndp5hxtnsd3skxefwxqzjcfedyzjunk4z60xest588de6jl3acyugt0n997zj2pqmrfsuw45ahknf2sztf3esf5550yp37mh52c7azpvzxkhxlmnx464whf7jskscpmrdj0a",
      "lnqc1700n1pwr7z98pp5j5r5q5c7syavjjz7czjvng4y95w0rd8zkl7q43sm7spg9ht2sjfqdquwf6kumnfdenjqmrfva58gmnfdenshmgcxlcs3ec8m0l4nds65l02mlfwzadn8nj53tryysq9vm8mq3jp30gsqmxrc46vyx68f272f75a9eue6jy6f004vkepmvex7vr0rjqp9tx8j7",
      "lnqc1500n1pdl05k5pp5nyd9netjpzn27slyj2np4slpmlz8dy69q7hygwm8ff4mey2jee5sdpa2fjkzep6ypxhjgz90pcx2unfv4hxxefqdanzqargv5s9xetrdahxggzvd9nksxqr23sgl0rhnm337km0g22d9maptglzxwpahqwvyv49nl7q8wrjvtt7egz7z7rcn240vcylaa0ul9ztg6n63lw7yvcwhlguy82munqzygmguqqd2mx3m",
      "lnqc1u1pwyvxrppp5nvm98wnqdee838wtfmhfjx9s49eduzu3rx0fqec2wenadth8pxqsdqdg9jxgg8sn7vgyxqr23svfaqfmuv5t20ypge259g8frausvf7ve3sxq4ka7rcepp8rytzze5mu5qfdhrjhejdsr85h9h439pj2fheq97pdq559ll28p729p83yspuplkh6",
      "lnqc10n1pw9qjwppp55nx7xw3sytnfle67mh70dyukr4g4chyfmp4x4ag2hgjcts4kydnsdpstfshq5n9v9jzucm0d5s8vmm5v5s8qmmnwssyj3p6yqenwd3cxqrrssjglj76sv6w2m939wswdhqfq5v6aurvpa5hdmuntgfnnywl4uc4sj26zpujsur5nhf5epq9nvce3a83wd9vk5k2zrln6rldn2huchfhgpf2082g",
      "lnqc10u1pw9x373pp549mpcznu3q0r4ml095kjg38pvsdptzja8vhpyvc2avatc2cegycsdzz2p6hycmgv9ek2gr0vcsrzgrxd3hhwetjyphkugzzd96xxmmfdcsywunpwejhjctjvsnaeyrz92lt8uuz95jzkce2mvqv2sh7hah0ktw8ats895n3twjnxpl0mhsszagd8em0k7u3s4gf6tmx5yaxchdlqfu9ujl3nflfzmvegphkmkfa",
      "lnqc1500n1pd7u7vupp54jm8s8lmgnnru0ndwpxhm5qwllkrarasr9fy9zkunf49ct8mw9ssdqvg9jxgg8zn2ssxqr23syqv6cx6uw9gnzq76fal2fdhpkzv8qusx4dp7c2nyll7fzsy532t40ykr50k29vkqx0647749hfpzjz274wv8tr4qwzl4r8jdluntd0qqt40jyx",
      "lnqc720n1pwypj4epp5k2saqsjznpvevsm9mzqfan3d9fz967x5lp39g3nwsxdkusps73csdzq2pshjmt9de6zqen0wgsrwv3qwp5hsetvwvsxzapqwdshgmmndp5hxtnsd3skxefwxqzjc4afl3tud5sv7y524c4flllvn2m0dsu25ewre24s0qsgfla60anfpw52y55r6kucj90em5lsd68kdgmzha3kvfvjlw4dudqjv0sjwa7cqzzdqxx",
      "lnqc10n1pwykdacpp5kegv2kdkxmetm2tpnzfgt4640n7mgxl95jnpc6fkz6uyjdwahw8sdpstfshq5n9v9jzucm0d5s8vmm5v5s8qmmnwssyj3p6yqenwdp5xqrrssjtg50fs4cw29js6n0sgf2akry857jy7s2cdjv7rc9mtg8tvk6c9qefjn33pu3frmz6hfkegq5hu8800ces4562h83kc28memrlylftgqk7ds5u",
      "lnqc3100n1pwp370spp5ku7y6tfz5up840v00vgc2vmmqtpsu5ly98h09vxv9d7k9xtq8mrsdpjd35kw6r5de5kueevypkxjemgw3hxjmn89ssxc6t8dp6xu6twvudhfveatdsu2y5v0e9klllufsdey3j4h6lk3cwncfrn32eg4phksyh6uhc4jp70r8slcyr5zsuqvjrs8nqr2vup6azjkfakcuv3tt2scpcx56ra",
      "lnqc1500n1pwr7z8rpp5hyfkmnwwx7x902ys52du8pph6hdkarnqvj6fwhh9swfsg5lp94vsdpa2fjkzep6ypph2um5dajxjctvypmkzmrvv468xgrpwfjjqetkd9kzqctwvss8yxqr23s7saqd97tv6f5dc7x6mnf5azzu0d9znwq4a0u0dsx6mxtlq3fepkxmhvyvhk9trlksrjz75sd0nwcwsx8423krk6vxfl66cpwf329slsq3eugt2",
      "lnqc1500n1pdl05v0pp5c4t5p3renelctlh0z4jpznyxna7lw9zhws868wktp8vtn8t5a8uqdpa2fjkzep6ypxxjemgw35kueeqfejhgam0wf4jqnrfw96kjerfw3ujq5r0dakq6xqr23s8p9jadysjcv2y3v3hq84xv0ka79h6z26xjnnhrvkz7z7m357zfry9vhsc3leyvlw0xmv458mal5j4qpntsvgssx7rs7mm4dx4hdxqvqq9apc6z",
      "lnqc1500n1pwyvxp3pp5ch8jx4g0ft0f6tzg008vr82wv92sredy07v46h7q3h3athx2nm2sdpa2fjkzep6ypyx7aeqfys8w6tndqsx67fqw35x2gzvv4jxwetjypvzqam0w4kxgxqr23s4a02vftzjr5v80cah86m6kcyrmmm8c8k7vgnjfmth0c0mjk0vm33r3q0e5sl82n37nehlvv4tluhmvfvytd5g8cxa7nakkf8phnnpsqqdu0vkd",
      "lnqc1500n1pwr7z2ppp5cuzt0txjkkmpz6sgefdjjmdrsj9gl8fqyeu6hx7lj050f68yuceqdqvg9jxgg8zn2ssxqr23s65sxrcm3z8rje8f5nf0xvj9gzf3vl0nvyxmcv2z8zp3umdkrf2x8jt6w0wqdgndy98p4p5tku66jdpmsj2ac08zlan36dyw85mwgd0sphu8r7w",
      "lnqc1500n1pd7u7g4pp5eam7uhxc0w4epnuflgkl62m64qu378nnhkg3vahkm7dhdcqnzl4sdqvg9jxgg8zn2ssxqr23sfkaw8dfcczeecx8t48wt49m8quzz87t9l4unlnpl2cgsug9syaukctwptvq3370h53y408rcahgdgmhsg2wzvfrtqlz5fzwnc9mztlgpr2fh64",
      "lnqc5u1pwq2jqzpp56zhpjmfm72e8p8vmfssspe07u7zmnm5hhgynafe4y4lwz6ypusvqdzsd35kw6r5de5kuemwv468wmmjddehgmmjv4ejucm0d40n2vpsta6hqan0w3jhxhmnw3hhye2fgs7nywfhvn6k28yum8y0qxfvatajjux2z8r3hn4y3f8a4yph62vj8g9cl6fjwg0kpndgxppyac44kkqsehuv94rqyzqvae4ydtkg5xh75ctz37cp4u06wn",
      "lnqc10n1pw9pqp3pp562wg5n7atx369mt75feu233cnm5h508mx7j0d807lqe0w45gndnqdpstfshq5n9v9jzucm0d5s8vmm5v5s8qmmnwssyj3p6yqenwdejxqrrssje0w9k7pnzd5dzw60p3qqp26zjyt087jglsa2gl5clqmscnnqz0xhacm9tqjen0wqttctflvp5svssfywa92q8eph4978rjqnreqmcgq2ga56a",
      "lnqc90n1pwypjnppp5m870lhg8qjrykj6hfegawaq0ukzc099ntfezhm8jr48cw5ywgpwqdpl2pshjmt9de6zqen0wgsrjgrsd9ux2mrnypshggrnv96x7umgd9ejuurvv93k2tsxqzjctmxewqenmv6gfhjczcht92qznuelc5gj8rytgels4rhrqepw2urn6n43jdffd676k8j5a8vwj34zn365utxupq5rmje30d66s9hzf4cq8skkzk",
      "lnqc100n1pdunsurpp5af2vzgyjtj2q48dxl8hpfv9cskwk7q5ahefzyy3zft6jyrc4uv2qdp0tfshq5n9v9jzucm0d5s8vmm5v5s8qmmnwssyj3p6yqcnyvcxqyd9uqp0734rcuy4s0ycmwg9vavtx6szq4jsr4uhlv0pxqhzswwld8wje3v8k86n0u6jdr856ql302x0p544yl3546kzsly26ukgjpuq5a4yqqrzkauw",
      "lnqc100n1pd6hzfgpp5au2d4u2f2gm9wyz34e9rls66q77cmtlw3tzu8h67gcdcvj0dsjdqdp0tfshq5n9v9jzucm0d5s8vmm5v5s8qmmnwssyj3p6yqcnqvsxqyd9uqn5pasys5w2m059lf49ddh4je5pkld4p0d2zynzrf5zvt2ewu333hsyqkshkxugegad8xk3lhpgam2x3hsnlyqy62ul6txexdfnq9macpfncfc7",
      "lnqc50n1pdl052epp57549dnjwf2wqfz5hg8khu0wlkca8ggv72f9q7x76p0a7azkn3ljsdp0tfshq5n9v9jzucm0d5s8vmm5v5s8qmmnwssyj3p6yqcnvvsxqyd9uq5gmqu9a30nlnfn5je4ejfjgzpjgyr2v6l2jmrfzuqpqazu4wyyynhuyawd8pz838vx7s6ral9y0tq8ech0867mplrv04a08u96wjcpsq90qd6e",
      "lnqc100n1pd7cwrypp57m4rft00sh6za2x0jwe7cqknj568k9xajtpnspql8dd38xmd7musdp0tfshq5n9v9jzucm0d5s8vmm5v5s8qmmnwssyj3p6yqcngvsxqyd9uqyyf6xrdz0acu5ht7df5f7c6pejepd4n0ytc57wzg7hsj6p2865jprr46mc9l482he37t4zwta8869gsqt4eyak6udqtsr4tvqcrtp2sq7sjayy",
      "lnqc100n1pw9qjdgpp5lmycszp7pzce0rl29s40fhkg02v7vgrxaznr6ys5cawg437h80nsdpstfshq5n9v9jzucm0d5s8vmm5v5s8qmmnwssyj3p6yqenwdejxqrrssr9kp40z8vlqdvs069002urmtvumw9fjq8jrp08dwty29tp6a4443cjjd6254hyeg8rhcf8lkqdwwavzz07skc54gx2m3djvskaral8gp9ujspt"
    )

    for (req <- requests) { assert(PaymentRequest.write(PaymentRequest.read(req)) == req) }
  }
}
