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

// In this version, scores are only weighted by word-document similarity. (i..e, not as in the paper)
case class W2KJNN(rstarwords: ElkiKNN, rstardocs: ElkiKNN, doclabels: Map[Vector[Double], Int], wordlabels: Map[Vector[Double], String],
                  repscores: Map[Double, Map[String, Double]],
                  var k: Int, var j: Int) extends FullSpaceOutlierDetector {
  val id = "W2KJNN"

  //Note using the cache easily leads to some GC overhead limit exceeded.
  //val dotcache: scala.collection.mutable.Map[(Array[Double], Array[Double]), Double] = scala.collection.mutable.Map[(Array[Double], Array[Double]), Double]()

  def setkj(newk: Int, newj: Int): Unit = {
    k = newk
    j = newj
  }

  // must be row-oriented
  def computeScores(instances: Array[Array[Double]]): Array[(Int, Double)] = {
    //val all_nnwords: Map[Array[Double], Array[Array[Double]]] =
    //  instances.zipWithIndex.map{case (doc,i) => doc -> rstarwords.getKNNValuesForObject(doc, k1)}.toMap

    val results = instances.zipWithIndex.map{case (doc,i) =>
      val nn_docs = rstardocs.getKNNValuesForObject(doc, k+1).drop(1)  // +1 because the first object is always self

      val docwordstowordsdistances: Array[(Int, Double)] = for{
        nn_doc <- nn_docs
      } yield {
        val score = if(j == 0) {
          (dot(doc, nn_doc) + 1.0)/ 2
        } else {
          val nn_docwords = rstarwords.getKNNValuesForObject(nn_doc, j)
          val distance = (dot(doc, nn_doc) + 1.0)/ 2
          distance * nn_docwords.map(x => repscores(doclabels(nn_doc.toVector)).getOrElse(wordlabels(x.toVector),0.0)).sum
        }
        (doclabels(nn_doc.toVector), score)
      }

      val grouped: Map[Int, Double] = docwordstowordsdistances.groupBy(_._1).map(x => (x._1, x._2.map(_._2).sum))
      val maxlabel: Int = grouped.maxBy(_._2)._1
      val sumdistance = grouped.values.sum
      val entropy: Double = if(sumdistance == 0.0) 1.0 else {
        - grouped.map(x => if(x._2 == 0) 0 else math.log(x._2/sumdistance)*(x._2/sumdistance)).sum
      }
      (maxlabel, entropy)
    }
    results
  }

}
