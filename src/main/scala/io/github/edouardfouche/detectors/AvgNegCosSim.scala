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

/**
  * Implementation of the ANCS/k-ANCS Baselines (Average Negative Cosine Similarity)
  */
case class AvgNegCosSim(rstardocs: ElkiKNN, var k: Int) extends FullSpaceOutlierDetector {
  val id = s"AvgNegCosSim"

  def setkj(newk: Int, newj: Int): Unit = {
    k = newk
  }

  /**
    * Compute the AvgNegCosSim for each element of a given array of instances.
    *
    * @param instances instances (rows) of a data set.
    * @return An arrax of 2-Tuple. The first element is the index, the second is the AvgNegCosSim.
    */
  def computeScores(instances: Array[Array[Double]]): Array[(Int, Double)] = {
    val results = instances.zipWithIndex.map{case (doc,i) =>
      val nn_docs = if(k >= instances.length-1) {
        instances
      } else rstardocs.getKNNValuesForObject(doc, k+1) // +1 because the first object is always self
      val distances = dot(doc, nn_docs)
      val mean = distances.tail.sum / k
      (i, -mean)
    }
    results
  }
}
