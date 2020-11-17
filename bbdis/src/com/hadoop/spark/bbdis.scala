package com.hadoop.spark
import org.apache.spark.SparkConf
import org.apache.spark.SparkContext
import scala.Array.concat

object bbdis {
  //def FILE_NAME : String = "file:///Users/litianfeng/Documents/scop.txt"
  def FILE_NAME: String = "/user/litianfeng/input/scop.txt"

  def APP_NAME: String = "b&b"

  def typeList: Array[String] = Array("string", "int")

  def SAVE_PATH1: String = "/user/litianfeng/output-bb1"

  def SAVE_PATH2: String = "/user/litianfeng/output-bb2"

  def MASTER_NAME: String = "spark://master.cluster:7077"

  //line中所有string全为数字，则返回true
  def isTypeInt(line: Seq[String]): Boolean = {
    val regex = """^\d+$""".r
    return line.forall(s => regex.findFirstMatchIn(s) != None)
  }

  //计算line <= base[?], 返回一个数组，如[1,2,3],表示第一列包含依赖于第二列和第三列。
  def calculateInd(line: (Seq[String], Int), base: Array[(Seq[String], Int)]) = {
    val lhs = Array(line._2)
    val rhss = base.filter(x => {
      val rhs = x._1.toSet
      line._1.forall(s => rhs.contains(s))
    }).map(x => x._2)
    concat(lhs, rhss)
  }

  def main(args: Array[String]): Unit = {
    val conf = new SparkConf().setAppName(APP_NAME).setMaster(MASTER_NAME)
    val sc = new SparkContext(conf)
    val textFile = sc.textFile(FILE_NAME)

    //分隔开
    val seperateTF = textFile.map(line => line.split(" ")).collect().toSeq
    //将行转置为列， 并在新的行前面加入该行的最小值和最大值，加速后续的包含判断
    val columnsData = sc.parallelize({
      val temp = seperateTF.transpose
      temp.foreach(x =>
        x.min +: x.max +: x
      )
      temp.zipWithIndex
    })

    //用typeIntData存储所有数字属性数据 NoInt存储字符
    val typeIntData = columnsData.filter(line => isTypeInt(line._1) == true)
    val typeNoIntData = columnsData.filter(line => isTypeInt(line._1) == false)
    //  .map(line=>(line._1.sorted,line._2)) //sort!
    //将两类数据全部发送到所有节点
    val baseIntData = sc.broadcast(typeIntData.collect)
    val baseNoIntData = sc.broadcast(typeNoIntData.collect)

    //两类数据分别计算ind
    val ind = typeNoIntData.map(line =>
      calculateInd(line, baseNoIntData.value)
    )
    val ind2 = typeIntData.map(line =>
      calculateInd(line, baseNoIntData.value)
    )

    //println("ind calc over!")
    ind.saveAsTextFile(SAVE_PATH1)
    ind2.saveAsTextFile(SAVE_PATH2)
  }
}