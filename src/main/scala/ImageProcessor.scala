import org.bytedeco.opencv.global.opencv_imgproc._
import org.bytedeco.opencv.global.opencv_imgcodecs._
import org.bytedeco.opencv.opencv_core._
import java.io.File

object ImageProcessor {
  def readImage(file: File): Mat = {
    val src = imread(file.getAbsolutePath)
    if (src.empty()) throw new Exception("画像が読み込めません")
    src
  }

  def createBinaryImage(src: Mat): Mat = {
    val gray = new Mat()
    val binary = new Mat()
    cvtColor(src, gray, COLOR_BGR2GRAY)

    // ノイズ除去
    GaussianBlur(gray, gray, new Size(3, 3), 0)

    // 二値化（文字を白、背景を黒にするためにINVを使用）
    threshold(gray, binary, 0, 255, THRESH_BINARY_INV | THRESH_OTSU)

    // 文字を横方向に膨張させて、隣り合う文字を一つの塊にする
    // これにより "1 2 3" が個別の輪郭ではなく、一つの大きな輪郭として検出される
    val kernel = getStructuringElement(MORPH_RECT, new Size(15, 3))
    morphologyEx(binary, binary, MORPH_DILATE, kernel)

    binary
  }

  def findContours(binary: Mat): MatVector = {
    val contours = new MatVector()
    org.bytedeco.opencv.global.opencv_imgproc.findContours(binary, contours, RETR_EXTERNAL, CHAIN_APPROX_SIMPLE)
    contours
  }

  def maskRegion(src: Mat, rect: Rect): Unit = {
    // 赤で塗りつぶす代わりにぼかし処理を行う
    // rectangle(src, rect, new Scalar(0, 0, 255, 0), -1, LINE_8, 0)

    // ROI（関心領域）を作成
    val roi = new Mat(src, rect)

    // ガウシアンブラーを適用
    // カーネルサイズ（奇数）を大きくするとぼかしが強くなる
    // SigmaXを大きくするとぼかしが強くなる
    GaussianBlur(roi, roi, new Size(51, 51), 30)

    // ROIは元の画像の参照を持っているので、roiへの変更はsrcに反映される
    // 明示的に書き戻す必要はないが、念のためclose等は不要（JavaCVのMatは自動管理ではないが、roiはsrcの部分参照）
  }

  def saveImage(file: File, image: Mat): Unit = {
    imwrite(file.getAbsolutePath, image)
  }
}