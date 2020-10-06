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
package io.github.edouardfouche.detectors

import io.github.edouardfouche.index.ElkiKNN
import io.github.edouardfouche.utils.dot

// In this version, scores are weighted both by document-document and by word-document similarities. (i..e, as in the paper)
case class WKJNN(rstarwords: ElkiKNN, rstardocs: ElkiKNN, doclabels: Map[Vector[Double], Int], wordlabels: Map[Vector[Double], String],
                 repscores: Map[Double, Map[String, Double]],
                 var k: Int, var j: Int) extends FullSpaceOutlierDetector {
  val id = "WKJNN"

  //Note using the cache easily leads to some GC overhead limit exceeded.
  //val dotcache: scala.collection.mutable.Map[(Array[Double], Array[Double]), Double] = scala.collection.mutable.Map[(Array[Double], Array[Double]), Double]()

  def setkj(newk: Int, newj: Int): Unit = {
    k = newk
    j = newj
  }

  def getWordRepresentativeness(instance: Array[Double]): Array[(String, Double)] = {
    val nn_docs = rstardocs.getKNNValuesForObject(instance, k+1).drop(1)

    val wordscores: Array[(String, Double)] = wordlabels.keys.toArray.map(x => (wordlabels(x), nn_docs.map(y => {
      repscores(doclabels(y.toVector)).getOrElse(wordlabels(x),0.0)
    }).sum/nn_docs.length))

    wordscores.sortBy(- _._2)
  }

  def getSimilarDocuments(instance: Array[Double], nbdoc: Int): Array[(Array[Double], Double)] = {
    val nn_docs = rstardocs.getKNNValuesForObject(instance, nbdoc+1).drop(1)

    nn_docs.map(x =>
      (x,(dot(instance, x) + 1.0)/ 2)
    ).sortBy(- _._2)
  }

  // must be row-oriented
  def computeScores(instances: Array[Array[Double]]): Array[(Int, Double)] = {
    val results = instances.zipWithIndex.map{case (doc,i) =>
      val scores: Array[(Int, Double)] = if(k > 0) {
        val nn_docs = rstardocs.getKNNValuesForObject(doc, k+1).drop(1)  // +1 because the first object is always self
        for{
          nn_doc <- nn_docs
        } yield {
          val score = if(j == 0) {
            (dot(doc, nn_doc) + 1.0)/ 2
          } else {
            val nn_docwords = rstarwords.getKNNValuesForObject(nn_doc, j)
            val similarity = (dot(doc, nn_doc) + 1.0)/ 2
            val relevance = nn_docwords.map(x => {
              val similarity2 = (dot(doc, x) + 1.0)/ 2
              similarity2 * repscores(doclabels(nn_doc.toVector)).getOrElse(wordlabels(x.toVector),0.0)
            }).sum
            similarity * relevance
          }
          (doclabels(nn_doc.toVector), score)
        }
      } else { // k= 0 -> only uses the j weighted relevance of words
        val classes = doclabels.values.toArray.distinct
        for{
          c <- classes
        } yield {
          val score = if(j == 0) {
            if(c == doclabels(doc.toVector)) 1 else 0
          } else {
            val nn_docwords = rstarwords.getKNNValuesForObject(doc, j)
            //val similarity = (dot(doc, nn_doc) + 1.0)/ 2
            val relevance = nn_docwords.map(x => {
              val similarity2 = (dot(doc, x) + 1.0)/ 2 // we do it weighted...
              similarity2 * repscores(c).getOrElse(wordlabels(x.toVector),0.0)
            }).sum
            //similarity *
            relevance
          }
          (c, score)
        }
      }

      val groupedscores: Map[Int, Double] = scores.groupBy(_._1).map(x => (x._1, x._2.map(_._2).sum))
      val maxlabel: Int = groupedscores.maxBy(_._2)._1
      val sumscores = groupedscores.values.sum
      val entropy: Double = if(sumscores == 0.0) 1.0 else {
        - groupedscores.map(x => if(x._2 == 0) 0 else math.log(x._2/sumscores)*(x._2/sumscores)).sum
      }
      (maxlabel, entropy)
    }
    results
  }
}
