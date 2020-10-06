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
package io.github.edouardfouche.experiments

import io.github.edouardfouche.detectors._
import io.github.edouardfouche.experiments.Data._
import io.github.edouardfouche.index.ElkiKNN
import io.github.edouardfouche.preprocess.DataSet
import io.github.edouardfouche.utils._

/**
  *
  */
object Evaluate_AvgNegCosSim   extends Experiment {
  def run(): Unit = {
    info(s"Starting com.edouardfouche.experiments ${this.getClass.getSimpleName}")

    val datasets = Vector("nyt_1", "nyt_2", "nyt_5", "nyt_10", "nyt_20", "nyt_50", "arxiv_15", "arxiv_25",
      "arxiv_35", "arxiv_45", "arxiv_55", "arxiv_54", "arxiv_53", "arxiv_52", "arxiv_51")
    val maxk = 100

    for{
      dataset <- datasets//.par
    } {
      val (words_emb, docs_emb, docs_labels, docstrings, wordsmap) = open(dataset)

      val docs: DataSet = docs_emb.open()
      val labelswithoutliers: Array[Double] = docs_labels.open()(0)
      val alldocs: Array[Array[Double]] = docs.columns.transpose

      info(s"Computing index for $dataset...")
      val (cpuindex, wallindex, rstardocs) = StopWatch.measureTime(ElkiKNN(docs))
      info(s"Index for $dataset done !")

      val ks: Vector[Int] = dynamicrange(1, maxk, 0.30) ++ Vector(docs.nrows-1)

      for {
        k <- ks
      } {
        val (cpu, wall, scores: Array[(Int, Double)]) = StopWatch.measureTime(AvgNegCosSim(rstardocs, k).computeScores(alldocs))

        val (typeAauc, typeAroc) = getAreaUnderCurveOfROCforTypeA(labelswithoutliers, scores)
        val typeAap = getAPforTypeA(labelswithoutliers, scores)

        val typeApr: Array[(Double, Double)] = getTypeARecallPrecisionAt125etc(labelswithoutliers, scores.map(x => (0,x._2)))

        val attributes = List("scoreId", "dataset", "k", "bmId", "ndocs", "emb", "cpuindex", "cpu", "wall", "typeAauc", "typeAap",
          "typeAr1", "typeAr2", "typeAr5", "typeAr10", "typeAr20", "typeAr30",
          "typeAp1", "typeAp2", "typeAp5",  "typeAp10",  "typeAp20",  "typeAp30")
        val summary = ExperimentSummary(attributes)
        summary.add("scoreId", "LOF")
        summary.add("dataset", dataset)
        summary.add("k", k)
        summary.add("bmId", words_emb.id.split("_")(0))
        summary.add("ndocs", docs.nrows)
        summary.add("emb", docs.ncols)
        summary.add("cpuindex", cpuindex)
        summary.add("cpu", cpu)
        summary.add("wall", wall)
        summary.add("typeAauc", "%.4f".format(typeAauc))
        summary.add("typeAap", "%.4f".format(typeAap))
        summary.add("typeAr1", "%.4f".format(typeApr(0)._1))
        summary.add("typeAr2", "%.4f".format(typeApr(1)._1))
        summary.add("typeAr5", "%.4f".format(typeApr(2)._1))
        summary.add("typeAr10", "%.4f".format(typeApr(3)._1))
        summary.add("typeAr20", "%.4f".format(typeApr(4)._1))
        summary.add("typeAr30", "%.4f".format(typeApr(5)._1))
        summary.add("typeAp1", "%.4f".format(typeApr(0)._2))
        summary.add("typeAp2", "%.4f".format(typeApr(1)._2))
        summary.add("typeAp5", "%.4f".format(typeApr(2)._2))
        summary.add("typeAp10", "%.4f".format(typeApr(3)._2))
        summary.add("typeAp20", "%.4f".format(typeApr(4)._2))
        summary.add("typeAp30", "%.4f".format(typeApr(5)._2))
        summary.write(summaryPath)

        info(s"AvgNegCosSim, $dataset : $k : " +
          s"AUC: $typeAauc, AP: $typeAap" +
          s"Recall @ 1:${"%.4f".format(typeApr(0)._1)}, 2:${"%.4f".format(typeApr(1)._1)}, 5:${"%.4f".format(typeApr(2)._1)}, 10:${"%.4f".format(typeApr(3)._1)}, 20:${"%.4f".format(typeApr(4)._1)}, 30:${"%.4f".format(typeApr(5)._1)}," +
          s"Precision @ 1:${"%.4f".format(typeApr(0)._2)}, 2:${"%.4f".format(typeApr(1)._2)}, 5:${"%.4f".format(typeApr(2)._2)}, 10:${"%.4f".format(typeApr(3)._2)}, 20:${"%.4f".format(typeApr(4)._2)}, 30:${"%.4f".format(typeApr(5)._2)},")
      }

      info(s"End of experiment ${this.getClass.getSimpleName} - ${formatter.format(java.util.Calendar.getInstance().getTime)}")
    }

  }
}
