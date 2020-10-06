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
import io.github.edouardfouche.preprocess.{DataSet, WordQuality}
import io.github.edouardfouche.utils._
import breeze.stats.DescriptiveStats.percentile

/**
  *
  */
object Entropy_KJNN   extends Experiment {
  def run(): Unit = {
    info(s"Starting com.edouardfouche.experiments ${this.getClass.getSimpleName}")

    val maxkj = 50
    val nrep = 10

    val ps = Vector(0.99, 0.98, 0.95, 0.90, 0.80, 0.70)
    val cs = Vector(0.0, 0.01, 0.02, 0.05, 0.1, 0.2, 0.3, 0.4, 0.5)
    val ks: Vector[Int] = Vector(1,2,5,10,20,30,40,50)
    val js: Vector[Int] = Vector(0) ++ Vector(1,2,5,10,20,30,40,50)

    for{
      dataset <- Vector("nyt_1")
    } {
      val (words_emb, docs_emb, docs_labels, docstrings, vec2word) = open(dataset)
      val allwords: DataSet = words_emb.open()
      val docs: DataSet = docs_emb.open()
      val labelswithoutliers: Array[Double] = docs_labels.open()(0)
      val classes: Array[Double] = labelswithoutliers.distinct.filter(_ != 0.0)
      val labels: Array[Double] = labelswithoutliers.map(x => if(x == 0.0) scala.util.Random.shuffle(classes.toList).head else x)
      val documents = docstrings

      val stop_source =  scala.io.Source.fromFile(fetch(s"/stopwords.txt"))
      val stopwords: Array[String] = stop_source.getLines().toArray
      stop_source.close()

      for {
        rep <- (1 to nrep).par
      } {
        info(s"Starting with repetition $rep")
        for {
          corruption <- cs.par
        } {
          info(s"Starting with NYT, c: ${corruption}")

          val corruptedlabels: Array[Double] = corruptlabels(labels, corruption)
          val alldocs = docs.columns.transpose
          info(s"alldocs.length : ${alldocs.length}, labels.length: ${corruptedlabels.length}")
          val labeldict: Map[Vector[Double], Int] = alldocs.zip(corruptedlabels).map(x => x._1.toVector -> x._2.toInt).toMap

          //val words = allwords
          val words: DataSet = new DataSet(allwords.columns.transpose.filter(x => !stopwords.contains(vec2word(x.toVector))).transpose)
          info(s"Pruned ${allwords.nrows - words.nrows} words (words.ncols: ${words.ncols}, words.nrows: ${words.nrows})")

          info(s"Getting repscores, c=${corruption}")
          val (cpurep, wallrep, repscores: Map[Double, Map[String, Double]]) = StopWatch.measureTime(new WordQuality(documents,
            phraseintegrity, corruptedlabels).estimate()) // before: nyt_labels: DataRef

          info(s"Examples, c=${corruption}:")
          repscores.keys.foreach { k =>
            val top = repscores(k).toArray.sortBy(x => -x._2).take(10)
            info(s"Cell $k, top: ${top mkString ";"}")
          }

          info(s"Caching kNN results with ${words_emb.id.split("_")(0)}...")

          info(s"Proportions c=${corruption}: ${labels.groupBy(identity).map(x => (x._1.toInt, x._2.length)).mkString(";")}")

          val (cpuindex1, wallindex1, rstarwords) =  StopWatch.measureTime(ElkiKNN(words))
          val (cpuindex2, wallindex2, rstardocs) =  StopWatch.measureTime(ElkiKNN(docs))

          val (cpucache, wallcache, dummy) = StopWatch.measureTime({
            rstarwords.fillcache(alldocs, maxkj)
            rstardocs.fillcache(alldocs, maxkj)
          })

          val detector = WKJNN(rstarwords, rstardocs, labeldict, vec2word, repscores, 1, 1)
          info(s"Cache for ${detector.id} is ready !")

          for {
            k <- ks//.par
          } {
            for {
              j <- js
            } {
              detector.setkj(k, j)
              val (cpu, wall, scores: Array[(Int, Double)]) = StopWatch.measureTime(detector.computeScores(alldocs))
              val entropies: Array[Double] = scores.map(_._2)

              for {
                p <- ps // Vector(1.0, 0.99, 0.95, 0.90, 0.85, 0.80, 0.75, 0.70, 0.65, 0.60, 0.55, 0.50)
              } {
                val entropythreshold = percentile(entropies, p)

                // Sort in ascending order, as we will have more confidence for them
                val typeBlist: Array[((Int, Double), Double)] = scores.zip(labels).filter(x => x._1._2 <= entropythreshold).sortBy(_._1._2)
                // Sort in descending order, as the most uncertains might be the "outliers"
                val typeAlist: Array[((Int, Double), Double)] = scores.zip(labels).filter(x => x._1._2 > entropythreshold) .sortBy(- _._1._2)

                val miss = getMisclassification(typeBlist.map(_._2), typeBlist.map(_._1))

                val filteredscores: Array[(Int, Double)] = scores.zip(corruptedlabels).map(x => if (x._1._2 <= entropythreshold) x._1 else (0, x._1._2 ))

                val (typeBrecall, typeBprecision) = getTypeBRecallPrecision(labelswithoutliers, corruptedlabels, filteredscores)
                val (typeArecall, typeAprecision) = getTypeARecallPrecision(labelswithoutliers, filteredscores)
                val (avgmiss, stdmiss, avgmatch, stdmatch) = getUncertainty(labelswithoutliers, filteredscores)

                val typeBpr: Array[(Double, Double)] = getTypeBRecallPrecisionAt125etc(labelswithoutliers, corruptedlabels, filteredscores) //TODO: OK ?
                val typeBprN: Array[(Double, Double)] = getTypeBRecallPrecisionAt125etcN(labelswithoutliers, corruptedlabels, filteredscores)

                val typeApr: Array[(Double, Double)] = getTypeARecallPrecisionAt125etc(labelswithoutliers, filteredscores)

                val (typeAauc, typeAroc) = getAreaUnderCurveOfROCforTypeA(labelswithoutliers, filteredscores)
                val typeAap = getAPforTypeA(labelswithoutliers, filteredscores)

                val attributes = List("scoreId", "dataset", "bmId", "k", "j", "nwords", "ndocs", "emb", "cpu", "wall", "cpurep", "cpuindex", "cpucache",
                  "c", "p", "entropythreshold", "miss", "typeBrecall", "typeBprecision", "typeArecall", "typeAprecision", "typeAauc", "typeAap",
                  "typeBr1", "typeBr2", "typeBr5", "typeBr10", "typeBr20", "typeBr30",
                  "typeBp1", "typeBp2", "typeBp5",  "typeBp10",  "typeBp20",  "typeBp30",
                  "typeBr1N", "typeBr2N", "typeBr5N", "typeBr10N", "typeBr20N", "typeBr30N",
                  "typeBp1N", "typeBp2N", "typeBp5N",  "typeBp10N",  "typeBp20N",  "typeBp30N",
                  "typeAr1", "typeAr2", "typeAr5", "typeAr10", "typeAr20", "typeAr30",
                  "typeAp1", "typeAp2", "typeAp5",  "typeAp10",  "typeAp20",  "typeAp30",
                  "avgmiss", "stdmiss", "avgmatch", "stdmatch", "rep")
                val summary = ExperimentSummary(attributes)
                summary.add("scoreId", detector.id)
                summary.add("dataset", dataset)
                summary.add("bmId", words_emb.id.split("_")(0))
                summary.add("k", k)
                summary.add("j", j)
                summary.add("nwords", words.nrows)
                summary.add("ndocs", docs.nrows)
                summary.add("emb", docs.ncols)
                summary.add("cpu", cpu)
                summary.add("wall", wall)
                summary.add("cpurep", cpurep)
                summary.add("cpuindex", cpuindex1 + cpuindex2)
                summary.add("cpucache", cpucache)
                summary.add("c", corruption)
                summary.add("p", p)
                summary.add("entropythreshold", entropythreshold)
                summary.add("miss", "%.4f".format(miss))
                summary.add("typeBrecall", "%.4f".format(typeBrecall))
                summary.add("typeBprecision", "%.4f".format(typeBprecision))
                summary.add("typeArecall", "%.4f".format(typeArecall))
                summary.add("typeAprecision", "%.4f".format(typeAprecision))
                summary.add("typeAauc", "%.4f".format(typeAauc))
                summary.add("typeAap", "%.4f".format(typeAap))
                summary.add("typeBr1", "%.4f".format(typeBpr(0)._1))
                summary.add("typeBr2", "%.4f".format(typeBpr(1)._1))
                summary.add("typeBr5", "%.4f".format(typeBpr(2)._1))
                summary.add("typeBr10", "%.4f".format(typeBpr(3)._1))
                summary.add("typeBr20", "%.4f".format(typeBpr(4)._1))
                summary.add("typeBr30", "%.4f".format(typeBpr(5)._1))
                summary.add("typeBp1", "%.4f".format(typeBpr(0)._2))
                summary.add("typeBp2", "%.4f".format(typeBpr(1)._2))
                summary.add("typeBp5", "%.4f".format(typeBpr(2)._2))
                summary.add("typeBp10", "%.4f".format(typeBpr(3)._2))
                summary.add("typeBp20", "%.4f".format(typeBpr(4)._2))
                summary.add("typeBp30", "%.4f".format(typeBpr(5)._2))
                summary.add("typeBr1N", "%.4f".format(typeBprN(0)._1))
                summary.add("typeBr2N", "%.4f".format(typeBprN(1)._1))
                summary.add("typeBr5N", "%.4f".format(typeBprN(2)._1))
                summary.add("typeBr10N", "%.4f".format(typeBprN(3)._1))
                summary.add("typeBr20N", "%.4f".format(typeBprN(4)._1))
                summary.add("typeBr30N", "%.4f".format(typeBprN(5)._1))
                summary.add("typeBp1N", "%.4f".format(typeBprN(0)._2))
                summary.add("typeBp2N", "%.4f".format(typeBprN(1)._2))
                summary.add("typeBp5N", "%.4f".format(typeBprN(2)._2))
                summary.add("typeBp10N", "%.4f".format(typeBprN(3)._2))
                summary.add("typeBp20N", "%.4f".format(typeBprN(4)._2))
                summary.add("typeBp30N", "%.4f".format(typeBprN(5)._2))
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
                summary.add("avgmiss", "%.4f".format(avgmiss))
                summary.add("stdmiss", "%.4f".format(stdmiss))
                summary.add("avgmatch", "%.4f".format(avgmatch))
                summary.add("stdmatch", "%.4f".format(stdmatch))
                summary.add("rep", rep)
                summary.write(summaryPath)

                info(s"${words_emb.id.split("_")(0)}, ${detector.id}($k, $j), c=${corruption}, p=$p, entropythreshold= $entropythreshold - miss: ${"%.4f".format(miss)}, " +
                  s"typeBrecall: ${"%.4f".format(typeBrecall)}, 1:${"%.4f".format(typeBpr(0)._1)}, 2:${"%.4f".format(typeBpr(1)._1)}, 5:${"%.4f".format(typeBpr(2)._1)}, 10:${"%.4f".format(typeBpr(3)._1)}, 20:${"%.4f".format(typeBpr(4)._1)}, 30:${"%.4f".format(typeBpr(5)._1)}," +
                  s"typeBprecision: ${"%.4f".format(typeBprecision)},  1:${"%.4f".format(typeBpr(0)._2)}, 2:${"%.4f".format(typeBpr(1)._2)}, 5:${"%.4f".format(typeBpr(2)._2)}, 10:${"%.4f".format(typeBpr(3)._2)}, 20:${"%.4f".format(typeBpr(4)._2)}, 30:${"%.4f".format(typeBpr(5)._2)}," +
                  s"typeArecall: ${"%.4f".format(typeArecall)}, 1:${"%.4f".format(typeApr(0)._1)}, 2:${"%.4f".format(typeApr(1)._1)}, 5:${"%.4f".format(typeApr(2)._1)}, 10:${"%.4f".format(typeApr(3)._1)}, 20:${"%.4f".format(typeApr(4)._1)}, 30:${"%.4f".format(typeApr(5)._1)}," +
                  s"typeAprecision: ${"%.4f".format(typeAprecision)},  1:${"%.4f".format(typeApr(0)._2)}, 2:${"%.4f".format(typeApr(1)._2)}, 5:${"%.4f".format(typeApr(2)._2)}, 10:${"%.4f".format(typeApr(3)._2)}, 20:${"%.4f".format(typeApr(4)._2)}, 30:${"%.4f".format(typeApr(5)._2)}," +
                  s"miss uncertainty: ${"%.4f".format(avgmiss)} +/- ${"%.4f".format(stdmiss)}, " +
                  s"match uncertainty: ${"%.4f".format(avgmatch)} +/- ${"%.4f".format(stdmatch)}, ")
              }
              info(s"Done with ${words_emb.id.split("_")(0)}, c=${corruption} for ${detector.id} ")
            }
          }
        }
        info(s"Finished repetition $rep")
    }
    }

    info(s"End of experiment ${this.getClass.getSimpleName} - ${formatter.format(java.util.Calendar.getInstance().getTime)}")
  }
}
