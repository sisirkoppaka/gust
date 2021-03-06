package gust.linalg.cuda

import breeze.linalg.qr.QR
import breeze.linalg.svd.SVD
import org.scalatest.{Outcome, BeforeAndAfterEach, FunSuite}
import jcuda.jcublas.{JCublas2, cublasHandle}
import breeze.linalg._
import jcuda.runtime.JCuda
import breeze.numerics.{abs, cos}
import jcuda.driver.JCudaDriver
import gust.util.cuda.CuContext

/**
 * TODO
 *
 * @author dlwh
 **/
class CuMatrixTest extends org.scalatest.fixture.FunSuite {

  type FixtureParam = cublasHandle

  def withFixture(test: OneArgTest):Outcome = {
    val handle = new cublasHandle()
    JCuda.setExceptionsEnabled(true)
    JCublas2.setExceptionsEnabled(true)
    JCublas2.cublasCreate(handle)

    try {
      withFixture(test.toNoArgTest(handle)) // "loan" the fixture to the test
    }
    finally JCublas2.cublasDestroy(handle)
  }



  test("fromDense and back") { (_handle: cublasHandle) =>
    implicit val handle = _handle
    val rand = convert(DenseMatrix.rand(10, 12), Float)
    val cumat = CuMatrix.zeros[Float](10, 12)
    cumat := rand
    val dense = cumat.toDense
    assert(dense === rand)
  }

  test("copy gpuside") { (_handle: cublasHandle) =>
    implicit val handle = _handle
    val rand = convert(DenseMatrix.rand(10, 12), Float)
    val cumat, cumat2 = CuMatrix.zeros[Float](10, 12)
    cumat := rand
    cumat2 := cumat
    val dense = cumat2.toDense
    assert(dense === rand)
  }

  test("fromDense transpose and back") { (_handle: cublasHandle) =>
    implicit val handle = _handle
    val rand: DenseMatrix[Float] = convert(DenseMatrix.rand(12, 10), Float)
    val cumat: CuMatrix[Float] = CuMatrix.zeros[Float](10, 12)
    cumat := rand.t
    val dense = cumat.toDense
    assert(dense.rows === rand.cols)
    assert(dense.cols === rand.rows)
    assert(dense === rand.t)
  }

  test("fromDense transpose and back 2") { (_handle: cublasHandle) =>
    implicit val handle = _handle
    val rand: DenseMatrix[Float] = convert(DenseMatrix.rand(12, 10), Float)
    val cumat: CuMatrix[Float] = CuMatrix.zeros[Float](10, 12)

    cumat.t := rand
    val dense2 = cumat.toDense
    assert(dense2.rows === rand.cols)
    assert(dense2.cols === rand.rows)
    assert(dense2 === rand.t)
  }

  test("copy transpose gpuside") { (_handle: cublasHandle) =>
    implicit val handle = _handle
    val rand: DenseMatrix[Float] = convert(DenseMatrix.rand(10, 12), Float)
    val cumat: CuMatrix[Float] = CuMatrix.zeros[Float](10, 12)
    val cumat2: CuMatrix[Float] = CuMatrix.zeros[Float](12, 10)
    cumat := rand
    cumat2 := cumat.t
    val dense = cumat2.toDense
    assert(dense.t === rand)
  }

  test("copy transpose gpuside 2") { (_handle: cublasHandle) =>
    implicit val handle = _handle
    val rand: DenseMatrix[Float] = convert(DenseMatrix.rand(10, 12), Float)
    val cumat: CuMatrix[Float] = CuMatrix.zeros[Float](10, 12)
    val cumat2: CuMatrix[Float] = CuMatrix.zeros[Float](12, 10)
    cumat := rand
    cumat2.t := cumat
    val dense = cumat2.toDense
    assert(dense.t === rand)
  }

  test("rand test") { (_handle: cublasHandle) =>
    implicit val handle = _handle
    val cumat = CuMatrix.rand(10, 12)
    val dense = cumat.toDense
    assert(all(dense))
    assert(dense.forallValues(_ < 1))
  }

  test("Multiply") { (_handle: cublasHandle) =>
    implicit val handle = _handle
    val a = DenseMatrix((1.0f, 2.0f, 3.0f),(4.0f, 5.0f, 6.0f))
    val b = DenseMatrix((7.0f, -2.0f, 8.0f),(-3.0f, -3.0f, 1.0f),(12.0f, 0.0f, 5.0f))
    val ga = CuMatrix.fromDense(a)
    val gb = CuMatrix.fromDense(b)

    assert( (ga * gb).toDense === DenseMatrix((37.0f, -8.0f, 25.0f), (85.0f, -23.0f, 67.0f)))


    val x = ga * ga.t
    assert(x.toDense === DenseMatrix((14.0f,32.0f),(32.0f,77.0f)))

    val y = ga.t * ga
    assert(y.toDense === DenseMatrix((17.0f,22.0f,27.0f),(22.0f,29.0f,36.0f),(27.0f,36.0f,45.0f)))

//    val z  = gb * (gb + 1.0f)
//    assert(z.toDense === DenseMatrix((164.0f,5.0f,107.0f),(-5.0f,10.0f,-27.0f),(161.0f,-7.0f,138.0f)))
  }

  test("Reshape") { (_handle: cublasHandle) =>
    implicit val handle = _handle
    val dm: DenseMatrix[Float] = convert(DenseMatrix.rand(20, 30), Float)
    val cu: CuMatrix[Float] = CuMatrix.zeros[Float](20, 30)
    cu := dm
    assert(cu.reshape(10, 60).toDense ===  dm.reshape(10, 60))
  }

  test("Slices") { (_handle: cublasHandle) =>
    implicit val handle = _handle
    val dm : DenseMatrix[Float] = convert(DenseMatrix.rand(20, 30), Float)
    val cu = CuMatrix.zeros[Float](20, 30)
    cu := dm

    assert(cu(0, ::).toDense === dm(0, ::).t.toDenseMatrix)
    assert(cu(0, 1 to 4).toDense === dm(0, 1 to 4).t.toDenseMatrix, s"Full matrix: $dm")
    assert(cu(::, 0).toDense === dm(::, 0).toDenseMatrix.t, s"${dm(::, 0)}")
    assert(cu(1 to 4, 0).toDense === dm(1 to 4, 0).toDenseMatrix.t, s"Full matrix: $dm")
    assert(cu.t(0, ::).toDense === dm.t(0, ::).t.toDenseMatrix)
    assert(cu.t(0, 1 to 4).toDense === dm.t(0, 1 to 4).t.toDenseMatrix, s"Full matrix: $dm")
    assert(cu.t(::, 0).toDense === dm.t(::, 0).toDenseMatrix.t, s"${dm(::, 0)}")
    assert(cu.t(1 to 4, 0).toDense === dm.t(1 to 4, 0).toDenseMatrix.t, s"Full matrix: $dm")

  }

  test("Basic mapping functions") { (_handle: cublasHandle) =>
    implicit val handle = _handle
    val dm : DenseMatrix[Float] = convert(DenseMatrix.rand(30, 10), Float)
    val cosdm = cos(dm)
    val cu = CuMatrix.zeros[Float](30, 10)
    cu := dm
    assert(cu.toDense === dm)
//    import CuMatrix.kernelsFloat
    val coscu = cos(cu)
    assert( max(abs(cosdm - coscu.toDense)) < 1E-5, s"$cosdm ${coscu.toDense}")

    cos.inPlace(coscu)
    assert( max(abs(cos(cosdm) - coscu.toDense)) < 1E-5, s"$cosdm ${coscu.toDense}")
  }

  test("Basic mapping functions transpose") { (_handle: cublasHandle) =>
    implicit val handle = _handle
    val dm : DenseMatrix[Float] = convert(DenseMatrix.rand(30, 10), Float)
    val cosdm: DenseMatrix[Float] = cos(dm)
    val cu = CuMatrix.zeros[Float](30, 10)
    cu := dm
    assert(cu.toDense === dm)
//    import CuMatrix.kernelsFloat
    val coscu = cos(cu.t)
//    val coscu = cu
    assert( max(abs(cosdm.t - coscu.toDense)) < 1E-5, s"$cosdm ${coscu.toDense}")

    cos.inPlace(coscu.t)
    assert( max(abs(cos(cosdm.t) - coscu.toDense)) < 1E-5, s"$cosdm ${coscu.toDense}")

  }

  test("Basic ops functions") { (_handle: cublasHandle) =>
    implicit val handle = _handle
    val dm : DenseMatrix[Float] = convert(DenseMatrix.rand(30, 10), Float)
    val cu = CuMatrix.zeros[Float](30, 10)
    cu := dm
    assert(cu.toDense === dm)
//    import CuMatrix.kernelsFloat
    val cu2 = cu + cu
    assert( max(abs((dm * 2.0f) - cu2.toDense)) < 1E-5)
    assert( max(abs((dm * 2.0f) - (cu * 2.0f).toDense)) < 1E-5)
  }

  test("addition") { (_handle: cublasHandle) =>
    implicit val handle = _handle
    val dm : DenseMatrix[Float] = convert(DenseMatrix.rand(30, 10), Float)
    val dm2 : DenseMatrix[Float] = convert(DenseMatrix.rand(10, 30), Float)
    val cu = CuMatrix.zeros[Float](30, 10)
    val cu2 = CuMatrix.zeros[Float](10, 30)
    cu := dm
    cu2 := dm2

    assert((dm + dm) === (cu + cu).toDense)
    assert((dm + dm2.t) === (cu + cu2.t).toDense)

  }

  test("transpose elemwise mul"){ (_handle: cublasHandle) =>
    implicit val handle = _handle
    val rows = 300
    val cols = 100
    val dm : DenseMatrix[Float] = convert(DenseMatrix.rand(rows, cols), Float)
    val dm2 : DenseMatrix[Float] = convert(DenseMatrix.rand(cols, rows), Float)
    val cu = CuMatrix.zeros[Float](rows, cols)
    val cu2 = CuMatrix.zeros[Float](cols, rows)
    cu := dm
    cu2 := dm2
    assert((dm :* dm2.t) === (cu :* cu2.t).toDense)
  }


  test("broadcast addition") { (_handle: cublasHandle) =>
    implicit val handle = _handle
    val dm : DenseMatrix[Float] = convert(DenseMatrix.rand(3, 3), Float)
    val cu = CuMatrix.zeros[Float](3, 3)
    cu := dm

    val dmadd = dm(::, *) + dm(::, 1)
    val cuadd = cu(::, *) + cu(::, 1)

    assert(cuadd.toDense === dmadd)

//    val dmadd2 =  dm(::, 1) + dm(::, *)
    val cuadd2 =  cu(::, 1) + cu(::, *)

    assert(cuadd2.toDense === dmadd)

  }

  test("inplace broadcast multiplication") {  (_handle: cublasHandle) =>
    implicit val handle = _handle
    val dm : DenseMatrix[Float] = convert(DenseMatrix.rand(3, 3), Float)
    val cu = CuMatrix.zeros[Float](3, 3)
    cu := dm

    val dm1 = copy(dm(::, 1))
    val cu1 = copy(cu(::, 1))
    dm(::, *) :*= dm1
    cu(::, *) :*= cu1

    assert(max(abs(cu.toDense - dm)) < 1E-4, (cu.toDense, dm))
  }

  test("inplace broadcast addition") {  (_handle: cublasHandle) =>
    implicit val handle = _handle
    val dm : DenseMatrix[Float] = convert(DenseMatrix.rand(3, 3), Float)
    val cu = CuMatrix.zeros[Float](3, 3)
    cu := dm

    val dm1 = copy(dm(::, 1))
    val cu1 = copy(cu(::, 1))
    dm(::, *) :+= dm1
    cu(::, *) :+= cu1

    assert(max(abs(cu.toDense - dm)) < 1E-4, (cu.toDense, dm))
  }

  test("sum") { (_handle: cublasHandle) =>
    implicit val handle = _handle
    val rand = convert(DenseMatrix.rand(60, 34), Float)
    val cumat = CuMatrix.zeros[Float](60, 34)
    cumat := rand
    assert((sum(cumat) - sum(rand))/sum(rand) < 1E-4)
  }

  test("sum cols") { (_handle: cublasHandle) =>
    implicit val handle = _handle
    val rand = convert(DenseMatrix.rand(33, 40), Float)
    val cumat = CuMatrix.zeros[Float](33, 40)
    cumat := rand
    val s1 = sum(cumat(::, *)).toDense
    val s2:DenseMatrix[Float] = sum(rand(::, *))
    assert(max(abs(s1 - s2)) < 1E-4, s"$s1 $s2")
  }

  test("sum rows") { (_handle: cublasHandle) =>
    implicit val handle = _handle
    val rand = convert(DenseMatrix.rand(33, 40), Float)
    val cumat = CuMatrix.zeros[Float](33, 40)
    cumat := rand
    val s1 = sum(cumat(*, ::)).toDense
    val s2 = sum(rand(*, ::)).toDenseMatrix
    assert(max(abs(s1 - s2)) < 1E-4, s"$s1 $s2")
  }

  test("max") { (_handle: cublasHandle) =>
    implicit val handle = _handle
    val rand = convert(DenseMatrix.rand(40, 40), Float)
    val cumat = CuMatrix.zeros[Float](40, 40)
    cumat := rand
    assert(max(cumat) === max(rand))
  }

  test("min") { (_handle: cublasHandle) =>
    implicit val handle = _handle
    val rand = convert(DenseMatrix.rand(40, 40), Float)
    val cumat = CuMatrix.zeros[Float](40, 40)
    cumat := rand
    assert(min(cumat) === min(rand))
  }

  test("softmax") { (_handle: cublasHandle) =>
    val rand = convert(DenseMatrix.rand(40, 30), Float)
    val cumat = CuMatrix.zeros[Float](40, 30)
    cumat := rand
    val cumax = softmax(cumat)
    val dmax = softmax(convert(rand, Double))
    assert(math.abs(cumax - dmax) < 1e-4)
  }

  test("softmax rows") { (_handle: cublasHandle) =>
    val rand = DenseMatrix.rand(40, 30)
    val cumat = CuMatrix.zeros[Float](40, 30)
    cumat := convert(rand, Float)
    val cumax = softmax(cumat(*, ::))
    val dmax = softmax(rand(*, ::))

    val dcumax = cumax.toDense.mapValues(_.toDouble).apply(::, 0)


    assert(max(abs(dcumax - dmax)) < 1E-4)

  }

  test("trace") { (_handle: cublasHandle) =>
    implicit val handle = _handle
    val rand: DenseMatrix[Float] = convert(DenseMatrix.rand(40, 40), Float)
    val cumat = CuMatrix.fromDense(rand)

    assert(Math.abs(trace(rand) - trace(cumat)) < 1e-5)
  }


  test("cond") { (_handle: cublasHandle) =>
    implicit val handle = _handle
    val rand: DenseMatrix[Float] = convert(DenseMatrix.rand(40, 40), Float)
    val cumat: CuMatrix[Float] = CuMatrix.fromDense(rand)

    val denseCond: Double = cond(convert(rand, Double))
    assert(Math.abs(denseCond - cond(cumat)) < 1e-3 * denseCond.abs, s"${denseCond} ${cond(cumat)}")
  }

  test("qr") {  (_handle: cublasHandle) =>
    implicit val handle = _handle
    val rand: DenseMatrix[Float] = convert(DenseMatrix.rand(40, 40), Float)
    val cumat: CuMatrix[Float] = CuMatrix.fromDense(rand)

    val QR(q, r) = qr(cumat)
    assert(max(abs(q * r - cumat)) < 1E-4)

  }

  test("svd") {  (_handle: cublasHandle) =>
    implicit val handle = _handle
    val rand: DenseMatrix[Float] = convert(DenseMatrix.rand(40, 40), Float)
    val cumat: CuMatrix[Float] = CuMatrix.fromDense(rand)

    val SVD(u, s, vt) = svd(cumat)
    assert((sum(u.t * u) - u.rows) < 1E-4)
    assert((sum(vt * vt.t) - vt.rows) < 1E-4)
    assert(max(abs(u * s * vt - cumat)) < 1E-4)

  }

//  test("chol") {  (_handle: cublasHandle) =>
//    implicit val handle = _handle
//    val rand = convert(DenseMatrix.rand[Double](60, 60), Double)
//    rand += rand.t
//    diag(rand) += 1E-4f
//    val cumat: CuMatrix[Double] = CuMatrix.fromDense(rand)
//
//    val c = cholesky(cumat).toDense
//    assert(max(abs(c * c.t - cumat.toDense)) < 1E-4)
//  }
}
