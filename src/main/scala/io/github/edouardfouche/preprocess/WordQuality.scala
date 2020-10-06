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
package io.github.edouardfouche.preprocess

class WordQuality(documents: Array[String], phraseintegrity: Array[(String, Double)], labeldocs: Array[Double]) {
  val celllabels: Array[Double] = labeldocs.distinct

  val scoreMap: scala.collection.mutable.Map[String, Double] = scala.collection.mutable.Map[String, Double](phraseintegrity:_*)
  val formatter = new java.text.SimpleDateFormat("yyy-MM-dd-HH-mm")

  def getintegrity(): Map[Double, scala.collection.mutable.Map[String, Double]] = {
    val cellscores: Map[Double, scala.collection.mutable.Map[String, Double]] = celllabels.map(x => x -> scoreMap).toMap
    cellscores
  }

  def estimate(): Map[Double, Map[String, Double]] = {
    val randid = scala.util.Random.nextInt(10000)

    println(s"Start WordQuality Estimation -- #$randid ${formatter.format(java.util.Calendar.getInstance().getTime)}")
    val cells: Map[Double, Array[String]] = documents.zip(labeldocs).groupBy(_._2).map({case (x,y) => (x, y.map(_._1))})

    println("Get integrity")
    val integrityscores: Map[Double, scala.collection.mutable.Map[String, Double]] = getintegrity()

    // initialize those guys
    println("Initialize counts")
    val counts: Map[Double, Map[Int, scala.collection.mutable.Map[String, Int]]] =
      celllabels.map(x =>
        x -> cells(x).indices.map(y =>
          y -> scala.collection.mutable.Map[String, Int]()).toMap).toMap

    println("Start counting")
    for{
      cell: Double <- cells.keys
    } {
      for {
        doc: (String, Int) <- cells(cell).zipWithIndex
      } {
        for{
          token: String <- doc._1.split(" ")
        } {
          val existence = scoreMap.getOrElse(token, -1)
          if(existence != -1) {
            counts(cell)(doc._2)(token) = counts(cell)(doc._2).getOrElse(token, 0) + 1
          }
        }
      }
    }
    println(s"Done counting, ${formatter.format(java.util.Calendar.getInstance().getTime)}")

    val tf: Map[Double, Map[String, Int]] =
      celllabels.map(x => {
        val tempcounts = scala.collection.mutable.Map[String, Int]()
        for{
          doc <- counts(x).keys
        } {
          for {
            phrase <- counts(x)(doc).keys
          } {
            tempcounts(phrase) = tempcounts.getOrElse(phrase, 0) + counts(x)(doc)(phrase)
          }
        }
        (x, tempcounts.toMap)
      }).toMap

    println(s"Done tf, ${formatter.format(java.util.Calendar.getInstance().getTime)}")

    val df: Map[Double, Map[String, Int]] =
      celllabels.map(x => {
        val tempcounts = scala.collection.mutable.Map[String, Int]()
        for{
          doc <- counts(x).keys
        } {
          for {
            phrase <- counts(x)(doc).keys
          } {
            tempcounts(phrase) = tempcounts.getOrElse(phrase, 0) + 1
          }
        }
        (x, tempcounts.toMap)
      }).toMap

    println(s"Done df, ${formatter.format(java.util.Calendar.getInstance().getTime)}")

    // the total count of phrases in each cell
    val cntP: Map[Double, Int] = tf.mapValues(x => x.values.sum)

    // number of siblings (we consider they are all siblings)
    val cntSib = celllabels.length - 1

    // maximum document frequency of any phrase in cell
    val maxDF: Map[Double, Int] = tf.mapValues(x => x.values.max)

    val avgCP: Map[Double, Double] = celllabels.map(x =>
      x -> (celllabels.map(cntP(_)).sum).toDouble / (cntSib + 1).toDouble).toMap

    println(s"Done computing the intermediate measure, ${formatter.format(java.util.Calendar.getInstance().getTime)}")

    val popularityscore: Map[Double, Map[String, Double]] = tf.mapValues(
      v => v.mapValues(z => math.log(z+1)))

    println(s"Done computing popularity, ${formatter.format(java.util.Calendar.getInstance().getTime)}")

    val k1 = 1.2
    val b = 0.75
    val ntf: Map[Double, Map[String, Double]] = tf.keys.map(
       k => (k,tf(k).mapValues(z => (z * (k1+1))/ (z + k1*(1-b+b * (cntP(k)/avgCP(k))))))).toMap

    println(s"Done computing ntf, ${formatter.format(java.util.Calendar.getInstance().getTime)}")

    val ndf: Map[Double, Map[String, Double]] = df.keys.map(
      k => (k,df(k).mapValues(z => math.log(1 + z)/math.log(1 + maxDF(k))))).toMap

    println(s"Done computing ndf, ${formatter.format(java.util.Calendar.getInstance().getTime)}")

    val rel : Map[Double,Map[String, Double]] = df.keys.map(
      k => (k,df(k).keys.map(z => (z,ndf(k)(z) * ntf(k)(z))).toMap)).toMap

    println(s"Done computing relevance, ${formatter.format(java.util.Calendar.getInstance().getTime)}")
    val distinctivenessscores: Map[Double,Map[String, Double]] = celllabels.map(x => x ->
      rel(x).keys.map(y => {
        (y, math.exp(rel(x)(y)) / (1+ celllabels.map(z => rel(z)).map(k => math.exp(k.getOrElse(y,0.0))).sum  ))
      }).toMap).toMap

    println(s"Done computing distinctiveness, ${formatter.format(java.util.Calendar.getInstance().getTime)}")

    println(s"End WordQuality Estimation #$randid -- ${formatter.format(java.util.Calendar.getInstance().getTime)}")

    celllabels.map(x => x ->
      phraseintegrity.map(y => {
        (y._1, integrityscores(x).getOrElse(y._1,0.0) * popularityscore(x).getOrElse(y._1,0.0) * distinctivenessscores(x).getOrElse(y._1,0.0))
      }).toMap).toMap
  }
}
