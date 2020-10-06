/*
 * Copyright (C) 2020 Edouard Fouché
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package io.github.edouardfouche

import java.io.{BufferedWriter, File, FileWriter}
import breeze.linalg.{*, DenseMatrix, DenseVector, sum}
import scala.annotation.tailrec

/**
  *
  */
package object utils {

  def dot(a: Array[Double], b: Array[Double]): Double = {
    DenseVector(a).dot(DenseVector(b))
  }
  // somehow memory leaks?
  def dot(aa: Array[Array[Double]], bb: Array[Array[Double]]): Array[Array[Double]] = {
    //aa.map(a => bb.map(b => DenseVector(a).dot(DenseVector(b)))) // equivalent to what we have below
    val left = DenseMatrix(aa: _*)
    val right= DenseMatrix(bb: _*)
    val distances = (left * right.t).t
    aa.indices.map(x => distances(::,x).toArray).toArray
  }

  def dotScala(aa: Array[Array[Double]], bb: Array[Array[Double]]): Array[Array[Double]] = {
    //aa.map(a => bb.map(b => DenseVector(a).dot(DenseVector(b)))) // equivalent to what we have below
    //val left = DenseMatrix(aa: _*)
    //val right= DenseMatrix(bb: _*)
    //val distances = (left * right.t).t
    //aa.indices.map(x => distances(::,x).toArray).toArray
    //aa.map(a => bb.map(b => DenseVector(a).dot(DenseVector(b)))) // equivalent to what we have below
    for{
      a <- aa
    } yield {
      for {
        b <- bb
      } yield {
        a.zip(b).map(x => x._1*x._2).sum
      }
    }
  }

  def cosine(aa: Array[Array[Double]], bb: Array[Array[Double]]): Array[Array[Double]] = {
    aa.map(a => bb.map(b => CosineSimilarity.cosineSimilarity(a,b)))
  }

  def cosine(a: Array[Double], b: Array[Double]): Double = {
    CosineSimilarity.cosineSimilarity(a,b)
  }

  def normeddot(aa: Array[Array[Double]], bb: Array[Array[Double]]): Array[Array[Double]] = {
    //aa.map(a => bb.map(b => DenseVector(a).dot(DenseVector(b)))) // equivalent to what we have below
    val left = DenseMatrix(aa: _*)
    val right= DenseMatrix(bb: _*)
    val distances = (left * right.t).t
    val normed = (distances + 1.0) /:/ 2.0
    aa.indices.map(x => normed(::,x).toArray).toArray
  }

  def relativedot(aa: Array[Array[Double]], bb: Array[Array[Double]]): Array[Array[Double]] = {
    val left = DenseMatrix(aa: _*)
    val right= DenseMatrix(bb: _*)
    val distances = (left * right.t).t
    val normed = (distances + 1.0) /:/ 2.0
    val relativedistances = normed(*,::) / sum(normed(::,*)).t
    aa.indices.map(x => relativedistances(::,x).toArray).toArray
  }

  def dot(a: Array[Double], bb: Array[Array[Double]]): Array[Double] = {
    //bb.map(b =>  DenseVector(a).dot(DenseVector(b)))
    val left = DenseVector(a)
    val right = DenseMatrix(bb: _*)
    (left.t * right.t).t.toArray
  }

  def relativedot(a: Array[Double], bb: Array[Array[Double]]): Array[Double] = {
    //bb.map(b =>  DenseVector(a).dot(DenseVector(b)))
    val left = DenseVector(a)
    val right = DenseMatrix(bb: _*)
    val distances = (left.t * right.t)
    val normed = (distances + 1.0) /:/ 2.0
    (normed/sum(normed)).t.toArray
  }

  def time[A](f: => A) = {
    val s = System.nanoTime
    val ret = f
    println("Time: " + (System.nanoTime - s) / 1e6 + "ms")
    ret
  }

  // expect rows
  def saveDataSet[T](res: Array[Array[T]], path: String): Unit = {
    val file = new File(path)
    val bw = new BufferedWriter(new FileWriter(file))
    bw.write(s"${(1 to res(0).length) mkString ","} \n") // a little header
    res.foreach(x => bw.write(s"${x mkString ","} \n"))
    bw.close()
  }

  def save[T](res: Array[T], path: String): Unit = {
    val file = new File(path)
    val bw = new BufferedWriter(new FileWriter(file))
    bw.write(res mkString ",")
    bw.close()
  }

  def createFolderIfNotExisting(path: String): Unit = {
    val directory = new File(path)
    if (!directory.exists()) {
      directory.mkdir()
    }
  }

  def initiateSummaryCSV(path: String, header: String): FileWriter = {
    val fileA = new File(path)
    val fwA = new FileWriter(fileA, true) // append set to true
    fwA.write(header) // this is the header
    fwA
  }

  def extractFieldNames[T <: Product](implicit m: Manifest[T]) = m.runtimeClass.getDeclaredFields.map(_.getName)
  // Should return (False positive rate, True positive rate)
  // https://en.wikipedia.org/wiki/Receiver_operating_characteristic
  // matches with sklearn.metrics.roc_curve
  def getROC(labels: Array[Boolean], predictions: Array[Double]): Array[(Double, Double)] = {
    require(labels.length == predictions.length, "labels and predictions should have the same length")
    val sortedPairs = labels.zip(predictions).sortBy(-_._2) // order the pairs by decreasing order of prediction
    val nP = labels.count(_ == true).toDouble // number of positives
    val nN = labels.length - nP // number of negatives
    val fP_inc = 1/nN
    val tP_inc = 1/nP
    @tailrec def cumulative(sortedPairs: Array[(Boolean, Double)], acc: (Double, Double), result: Array[(Double, Double)]): Array[(Double, Double)] = {
      if(sortedPairs.isEmpty) result
      else if(sortedPairs.head._1) {
        val newAcc = (acc._1, acc._2 + tP_inc)
        cumulative(sortedPairs.tail, newAcc, result :+ newAcc)
      } else {
        val newAcc = (acc._1 + fP_inc, acc._2)
        cumulative(sortedPairs.tail, newAcc, result :+ newAcc)
      }
    }
    cumulative(sortedPairs, (0,0), Array())
  }

  def getPR(labels: Array[Boolean], predictions: Array[Double]): Array[(Double, Double)] = {
    require(labels.length == predictions.length, "labels and predictions should have the same length")
    val pairs: Array[(Boolean, Double)] = labels.zip(predictions)
    //val nP: Double = labels.count(_ == true).toDouble // total number of positives
    //val nN = labels.length - nP // total number of negatives
    //val P_inc = 1/nN
    //val R_inc = 1/nP

    val thresholds = pairs.map(_._2).distinct.sorted.reverse

    val pr: Array[(Double, Double)] = for{threshold <- thresholds} yield {
      val top = pairs.filter(x => x._2 >= threshold)
      val tp = top.map(_._1).count(_ == true).toDouble
      val fp = top.map(_._1).count(_ == false).toDouble
      val fn = pairs.filter(x => x._2 < threshold).map(_._1).count(_ == true).toDouble
      val precision = tp / (tp + fp)
      val recall = tp/(tp+fn)
      (precision, recall)
    }
    //pr.sortBy(_._2)
    (1.0, 0.0) +: pr
  }

  // Turns out to return the same as AP now.
  // however, not inline with pr auc from scikit-learn
  def getAreaUnderCurveOfPR(labels: Array[Double], predictions: Array[(Int, Double)]): (Double, Array[(Double, Double)]) = {
    require(labels.length == predictions.length, "labels and predictions should have the same length")
    val fpr_tpr = getPR(labels.map(x => if(x == 1.0) true else false), predictions.map(_._2)).sortBy(_._2)
    @tailrec def cumulative(fpr_tpr: Array[(Double, Double)], res: Double): Double = {
      if(fpr_tpr.length == 1) res
      else cumulative(fpr_tpr.tail, res + (fpr_tpr.tail.head._2 - fpr_tpr.head._2)*fpr_tpr.tail.head._1)
    }
    (cumulative(fpr_tpr, 0.0), fpr_tpr)
  }

  // not supposed to be the same as getAreaUnderCurveOfPR ?
  // [Zhang and Zhang,2009] https://imada.sdu.dk/~zimek/InvitedTalks/TUVienna-2016-05-18-outlier-evaluation.pdf
  def getAPold(labels: Array[Double], predictions: Array[(Int, Double)]): Double = {
    require(labels.length == predictions.length, "labels and predictions should have the same length")
    val sortedpredictions = predictions.sortBy(-_._2)

    val precisionsat = for{indices <- sortedpredictions.indices} yield {
      val top = sortedpredictions.take(indices+1)
      val toplabels: Array[Double] = top.map(x => labels(x._1))
      val tp = toplabels.count(_ == 1.0).toDouble
      val fp = toplabels.count(_ != 1.0).toDouble
      tp / (tp + fp)
    }
    //print(precisionsat mkString ",")
    precisionsat.sum / precisionsat.length
  }

  def getMisclassification(labels: Array[Double], predictions: Array[(Int, Double)]): Double = {
    var miss = 0
    for{
      pair <- labels.zip(predictions)
    } {
      if(pair._1.toInt != pair._2._1) {
        miss += 1
      }
    }
    miss.toDouble / labels.length.toDouble
  }

  def getTypeBRecallPrecision(labels: Array[Double], corruptedlabels: Array[Double], predictions: Array[(Int, Double)]): (Double, Double) = {
    var fp = 0
    var fn = 0
    var tp = 0
    var tn = 0
    var omatch = 0
    var omiss = 0

    for{
      i <- labels.indices
    } {
      if(labels(i) == 0.0) {
        // in that case, it is an outlier (i.e., a type A)
        if(predictions(i)._1 == 0) { // and we recognize that it is one (i.e., not a type B)
          tn += 1
          omatch += 1
        } else {
          if(predictions(i)._1 != corruptedlabels(i).toInt) { // and we declared that it is a type B (false positive)
            fp += 1
          } else { // and we did not declare that it is a type B (true negative)
            tn += 1
          }
          omiss += 1
        }
      } else {
        if(labels(i) != corruptedlabels(i)) { // in that case it was corrupted
          if(predictions(i)._1 == 0) { // and we thought it was a type A (did not find it)
            fn += 1
          } else if(predictions(i)._1 != corruptedlabels(i).toInt) { // and we found it //TODO: Note that it is different than doing predictions(i)._1 == labels(i)
            tp += 1
          } else { // and we did  not found it
            fn += 1
          }
        } else { // not corrupted
          if(predictions(i)._1 == 0) { // and we thought it was a type A (did not fire a type B)
            tn += 1
          } else if(predictions(i)._1 == labels(i)) { // and we did not fire
            tn += 1
          } else { // and we did fire
            fp += 1
          }
        }
      }
    }
    println(s"TypeB : tp + fp = ${(tp.toDouble + fp.toDouble)}, tp= $tp, fp= $fp, tn= $tn, fn= $fn, omatch= $omatch, omiss = $omiss")
    val precision = if((tp.toDouble + fp.toDouble)== 0) 0.0 else tp.toDouble / (tp.toDouble + fp.toDouble)
    val recall = if((tp.toDouble + fn.toDouble)== 0) 0.0 else tp.toDouble / (tp.toDouble + fn.toDouble)
    (recall, precision)
  }

  def getTypeARecallPrecision(labels: Array[Double], predictions: Array[(Int, Double)]): (Double, Double) = {
    var fp = 0
    var fn = 0
    var tp = 0
    var tn = 0

    for{
      i <- labels.indices
    } {
      if(labels(i) == 0.0) { // in that case, it is actually an outlier
        if(predictions(i)._1 == 0) {
          tp += 1
        } else {
          fn += 1
        }
      } else {
        if(predictions(i)._1 == 0) {
          fp += 1
        } else {
          tn += 1
        }
      }
    }
    println(s"TypeA: tp + fp = ${(tp.toDouble + fp.toDouble)}, tp= $tp, fp= $fp, tn= $tn, fn= $fn")
    val precision = if((tp.toDouble + fp.toDouble)== 0) 0.0 else tp.toDouble / (tp.toDouble + fp.toDouble)
    val recall = if((tp.toDouble + fn.toDouble)== 0) 0.0 else tp.toDouble / (tp.toDouble + fn.toDouble)
    (recall, precision)
  }

  def getTypeBRecallPrecisionAt125etc(labels: Array[Double], corruptedlabels: Array[Double], predictions: Array[(Int, Double)]): Array[(Double,Double)] = {
    val result = Array(1,2,5,10,20,30).map{p =>
      val top_predictionsindexes = predictions.zip(corruptedlabels).zipWithIndex.
        filter(x => x._1._1._1 != x._1._2). // take only those where we claim a type B
        sortBy(_._1._1._2). // sort by increasing entropy
        take(math.floor((predictions.length/100.0) * p).toInt) // take only the top percent
      val top_predictions = top_predictionsindexes.map(x => x._1._1)
      val indexes = top_predictionsindexes.map(x => x._2)
      //val top_predictions = predictions.sortBy(_._2).take(math.floor((predictions.length/100.0) * p).toInt)
      //val top_labels = top_predictions.map(x => labels(x._1))
      val top_labels: Array[Double] = indexes.map(x => labels(x))
      val top_corruptedlabels: Array[Double] = indexes.map(x => corruptedlabels(x))

      val nTypeB = labels.zip(corruptedlabels).count(x => (x._1 != x._2) & (x._1 != 0))

      var fp = 0
      var fn = 0
      var tp = 0
      var tn = 0
      var omatch = 0
      var omiss = 0

      for{
        i <- top_labels.indices
      } {
        if(top_labels(i) == 0.0) {
          // in that case, it is an outlier (i.e., a type A)
          if(top_predictions(i)._1 == 0) { // and we recognize that it is one (i.e., not a type B)
            tn += 1
            omatch += 1
          } else {
            if(top_predictions(i)._1 != top_corruptedlabels(i).toInt) { // and we declared that it is a type B
              fp += 1
            } else { // and we did not declare that it is a type B
              tn += 1
            }
            omiss += 1
          }
        } else {
          if(top_labels(i) != top_corruptedlabels(i)) { // in that case it was corrupted
            if(top_predictions(i)._1 == 0) { // and we thought it was a type A (did not find it)
              fn += 1
            } else if(top_predictions(i)._1 != top_corruptedlabels(i).toInt) { // and we found out //TODO: Note that it is different than doing top_predictions(i)._1 == top_labels(i)
              tp += 1
            } else { // and we did  not found it
              fn += 1
            }
          } else { // not corrupted
            if(top_predictions(i)._1 == 0) { // and we thought it was a type A (did not fire)
              tn += 1
            } else if(top_predictions(i)._1 == top_labels(i).toInt) { // and we did not fire
              tn += 1
            } else { // and we did fire
              fp += 1
            }
          }
        }

      }
      println(s"p: $p -- nTypeB = ${nTypeB}, tp= $tp, fp= $fp, tn= $tn, fn= $fn, omatch= $omatch, omiss= $omiss")

      val precision = if((tp.toDouble + fp.toDouble)== 0) 0.0 else tp.toDouble / (tp.toDouble + fp.toDouble)
      //val recall = tp.toDouble / nTypeB.toDouble // would be small if corruption is large
      val recall = if((tp.toDouble + fn.toDouble)== 0) 0.0 else tp.toDouble / nTypeB.toDouble //(tp.toDouble + fn.toDouble)
      (recall, precision)
    }
    result
  }

  // This version does not sort by increasing entropy
  def getTypeBRecallPrecisionAt125etcN(labels: Array[Double], corruptedlabels: Array[Double], predictions: Array[(Int, Double)]): Array[(Double,Double)] = {
    val result = Array(1,2,5,10,20,30).map{p =>
      val top_predictionsindexes = predictions.zip(corruptedlabels).zipWithIndex.
        filter(x => x._1._1._1 != x._1._2). // take only those where we claim a type B
        //sortBy(_._1._1._2). // sort by increasing entropy
        take(math.floor((predictions.length/100.0) * p).toInt) // take only the top percent
      val top_predictions = top_predictionsindexes.map(x => x._1._1)
      val indexes = top_predictionsindexes.map(x => x._2)
      //val top_predictions = predictions.sortBy(_._2).take(math.floor((predictions.length/100.0) * p).toInt)
      //val top_labels = top_predictions.map(x => labels(x._1))
      val top_labels: Array[Double] = indexes.map(x => labels(x))
      val top_corruptedlabels: Array[Double] = indexes.map(x => corruptedlabels(x))

      //val nTypeB = labels.zip(corruptedlabels).count(x => x._1 != x._2)
      val nTypeB = labels.zip(corruptedlabels).count(x => (x._1 != x._2) & (x._1 != 0))

      var fp = 0
      var fn = 0
      var tp = 0
      var tn = 0
      var omatch = 0
      var omiss = 0

      for{
        i <- top_labels.indices
      } {
        if(top_labels(i) == 0.0) {
          // in that case, it is an outlier (i.e., a type A)
          if(top_predictions(i)._1 == 0) { // and we recognize that it is one (i.e., not a type B)
            tn += 1
            omatch += 1
          } else {
            if(top_predictions(i)._1 != top_corruptedlabels(i).toInt) { // and we declared that it is a type B
              fp += 1
            } else { // and we did not declare that it is a type B
              tn += 1
            }
            omiss += 1
          }
        } else {
          if(top_labels(i) != top_corruptedlabels(i)) { // in that case it was corrupted
            if(top_predictions(i)._1 == 0) { // and we thought it was a type A (did not find it)
              fn += 1
            } else if(top_predictions(i)._1 != top_corruptedlabels(i).toInt) { // and we found out //TODO: Note that it is different than doing top_predictions(i)._1 == top_labels(i)
              tp += 1
            } else { // and we did  not found it
              fn += 1
            }
          } else { // not corrupted
            if(top_predictions(i)._1 == 0) { // and we thought it was a type A (did not fire)
              tn += 1
            } else if(top_predictions(i)._1 == top_labels(i).toInt) { // and we did not fire
              tn += 1
            } else { // and we did fire
              fp += 1
            }
          }
        }

      }
      println(s"p: $p -- nTypeBN = ${nTypeB}, tp= $tp, fp= $fp, tn= $tn, fn= $fn, omatch= $omatch, omiss= $omiss")

      val precision = if((tp.toDouble + fp.toDouble)== 0) 0.0 else tp.toDouble / (tp.toDouble + fp.toDouble)
      //val recall = tp.toDouble / nTypeB.toDouble // would be small if corruption is large
      val recall = if((tp.toDouble + fn.toDouble)== 0) 0.0 else tp.toDouble / nTypeB.toDouble //(tp.toDouble + fn.toDouble)
      (recall, precision)
    }
    result
  }

  def getTypeARecallPrecisionAt125etc(labels: Array[Double], predictions: Array[(Int, Double)]): Array[(Double,Double)] = {
    val result = Array(1,2,5,10,20,30).map{p =>
      val (top_predictions, indexes) = predictions.zipWithIndex.sortBy(- _._1._2).take(math.floor((predictions.length/100.0) * p).toInt).unzip
      //val top_predictions = predictions.sortBy(_._2).take(math.floor((predictions.length/100.0) * p).toInt)
      //val top_labels = top_predictions.map(x => labels(x._1))
      val top_labels: Array[Double] = indexes.map(x => labels(x))
      val nTypeA = labels.count(x => x == 0.0)

      var fp = 0
      var fn = 0
      var tp = 0
      var tn = 0

      for{
        i <- top_labels.indices
      } {
        if(top_labels(i) == 0.0) { // in that case it was an outlier
          if(top_predictions(i)._1 == 0) { // and we detected it
            tp += 1
          } else { // and we did not detect it
            fn += 1
          }
        } else { // in that case it was not an outlier
          if (top_predictions(i)._1 == 0) { // and we said it is
            fp += 1
          } else { // and we did not say it is
            tn += 1
          }
        }
      }
      println(s"p: $p -- nTypeA = ${nTypeA}, tp= $tp, fp= $fp, tn= $tn, fn= $fn")
      val precision = if((tp.toDouble + fp.toDouble)== 0) 0.0 else tp.toDouble / (tp.toDouble + fp.toDouble)
      //val recall = tp.toDouble / nTypeB.toDouble // would be small if corruption is large
      val recall = if((tp.toDouble + fn.toDouble)== 0) 0.0 else tp.toDouble / nTypeA.toDouble //(tp.toDouble + fn.toDouble)
      (recall, precision)
    }
    result
  }

  def getMisclassificationByClass(labels: Array[Double], predictions: Array[(Int, Double)]): Array[(Int, Double)] = {
    val classes = labels.distinct
    for {
      c <- classes
    } yield {
      var miss = 0
      for{
        pair <- labels.zip(predictions)
      } {
        if((pair._1.toInt == c) && (pair._1.toInt != pair._2._1)) {
          miss += 1
        }
      }
      (c.toInt, miss.toDouble / labels.count(_ == c).toDouble)
    }
  }

  def getUncertainty(labels: Array[Double], predictions: Array[(Int, Double)]): (Double, Double, Double, Double) = {
    var entropy_miss = Array[Double]()
    var entropy_match = Array[Double]()
    for{
      pair <- labels.zip(predictions)
    } {
      if(pair._1.toInt != 0) {
        if(pair._1.toInt != pair._2._1) {
          entropy_miss = entropy_miss :+ pair._2._2
        } else {
          entropy_match = entropy_match :+ pair._2._2
        }
      }
    }
    (entropy_miss.sum/entropy_miss.length, breeze.stats.stddev(entropy_miss), entropy_match.sum/entropy_match.length, breeze.stats.stddev(entropy_match))
  }

  // This is in alignment with sklearn.metrics.average_precision_score and Honglei's AP
  def getAP(labels: Array[Double], predictions: Array[(Int, Double)]): Double = {
    require(labels.length == predictions.length, "labels and predictions should have the same length")
    val sortedpredictions = predictions.sortBy(-_._2)

    var AP = 0.0
    var i = 0.0
    var p = 0.0

    for{
      indices <- sortedpredictions.indices
    } {
      val label = labels(sortedpredictions(indices)._1)
      i = i + 1
      p = p + label
      AP = AP + label * (p / i)
    }
    //println(s"$i, $p, $AP")
    if(p == 0) 0
    else AP / p
  }

  def getAPforTypeA(labels: Array[Double], predictions: Array[(Int, Double)]): Double = {
    require(labels.length == predictions.length, "labels and predictions should have the same length")
    val sortedindexes = predictions.zipWithIndex.sortBy(-_._1._2).map(_._2)

    var AP = 0.0
    var i = 0.0
    var p = 0.0

    for{
      indices <- sortedindexes.indices
    } {
      val label = labels(sortedindexes(indices))
      i = i + 1
      if(label == 0.0) {
        p = p + 1.0
        AP = AP + 1.0 * (p / i)
      }
    }
    //println(s"$i, $p, $AP")
    if(p == 0) 0
    else AP / p
  }

  // matches with sklearn.metrics.roc_curve (auc)
  def getAreaUnderCurveOfROC(labels: Array[Double], predictions: Array[(Int, Double)]): (Double, Array[(Double, Double)]) = {
    require(labels.length == predictions.length, "labels and predictions should have the same length")
    val fpr_tpr = getROC(labels.map(x => if(x == 1.0) true else false), predictions.map(_._2))
    @tailrec def cumulative(fpr_tpr: Array[(Double, Double)], res: Double): Double = {
      if(fpr_tpr.length == 1) res
      else cumulative(fpr_tpr.tail, res + (fpr_tpr.tail.head._1 - fpr_tpr.head._1)*fpr_tpr.head._2)
    }
    (cumulative(fpr_tpr, 0.0), fpr_tpr)
  }

  def getAreaUnderCurveOfROCforTypeA(labels: Array[Double], predictions: Array[(Int, Double)]): (Double, Array[(Double, Double)]) = {
    require(labels.length == predictions.length, "labels and predictions should have the same length")
    val binlabels = labels.map(x => if(x == 0.0) 1.0 else 0.0)
    //val binpredictions = predictions.map(x => if(x._1 == 0) (1.0, x._2) else (0.0, x._2))
    val fpr_tpr = getROC(binlabels.map(x => if(x == 1.0) true else false), predictions.map(_._2))
    @tailrec def cumulative(fpr_tpr: Array[(Double, Double)], res: Double): Double = {
      if(fpr_tpr.length == 1) res
      else cumulative(fpr_tpr.tail, res + (fpr_tpr.tail.head._1 - fpr_tpr.head._1)*fpr_tpr.head._2)
    }
    (cumulative(fpr_tpr, 0.0), fpr_tpr)
  }

  def getPrecisionRecallAt125(labels: Array[Double], predictions: Array[(Int, Double)]): Array[(Double,Double)] = {
    val result = Array(1,2,5).map{p =>
      val top_predictions = predictions.sortBy(- _._2).take(math.floor((predictions.length/100.0) * p).toInt)
      val top_labels = top_predictions.map(x => labels(x._1))
      val noutliers = labels.sum
      val recall = (top_labels).sum / noutliers.toDouble
      val precision = top_labels.sum / top_labels.length.toDouble
      (precision, recall)
    }
    result
  }

  // Normalize to unit vector
  // expects rows
  def maptounitvector(data: Array[Array[Double]]): Array[Array[Double]] = {
    data.map(x => {
      val norm = math.sqrt(x.map(y => math.pow(y,2)).sum)
      x.map(y => y / norm)
    })
  }

  def dynamicrange(start: Int, max: Int, inc: Double): Vector[Int] = {
    @tailrec
    def acc(current: Int, output: Vector[Int]): Vector[Int] = {
      val newval = current + (current*inc).toInt.max(1)
      if(newval >= max) output :+ max
      else acc(newval, output :+ newval)
    }
    acc(start, Vector(start))
  }
}
