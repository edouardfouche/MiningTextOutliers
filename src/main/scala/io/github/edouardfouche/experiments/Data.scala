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

import io.github.edouardfouche.preprocess.DataRef

import scala.io.BufferedSource

object Data {
 val currentdir: String = System.getProperty("user.dir")

 def fetch(path: String): String = try {
  val fullpath = currentdir + "/data" + path
  val s = scala.io.Source.fromFile(fullpath)
  s.close()
  fullpath
 } catch {
  case _: Throwable => {
   try {
    val fullpath2 = currentdir + "/git/data" + path
    val s = scala.io.Source.fromFile(fullpath2)
    s.close()
    fullpath2
   } catch {
    case _: Throwable => {
     val message = s"Tried ${currentdir + "/data" + path} and ${currentdir + "/git/MiningTextOutliers/data" + path}"
     throw new Error(message)
    }
   }
  }
 }

 def open(dataname: String): (DataRef, DataRef, DataRef, Array[String], Map[Vector[Double], String]) = {
   lazy val words_emb = DataRef(s"${dataname}_words_x", fetch(s"/benchmark/${dataname}/${dataname}_cleaned_seg_prep_emb_w.txt"), 1, " ", "embedding", excludeIndex = true)
   lazy val docs_emb = DataRef(s"${dataname}_docs_x", fetch(s"/benchmark/${dataname}/${dataname}_cleaned_seg_prep_emb_d.txt"), 1, " ", "embedding", excludeIndex = true)
   lazy val docs_labels = DataRef(s"${dataname}_labels", fetch(s"/benchmark/${dataname}/${dataname}_labels_prep.txt"), 0, "\t", "labels", excludeIndex = true)
   val docs_source =  scala.io.Source.fromFile(fetch(s"/benchmark/${dataname}/${dataname}_cleaned_seg_prep.txt"))
   val docstrings: Array[String] = docs_source.getLines().toArray
   docs_source.close()
   val words_source = scala.io.Source.fromFile(fetch(s"/benchmark/${dataname}/${dataname}_cleaned_seg_prep_emb_w.txt"))
   val wordsmap: Map[Vector[Double], String] = words_source.getLines().drop(1).map(x => x.split(' ')).map(x => (x.tail.map(_.toDouble).toVector, x.head)).toArray.toMap
   words_source.close()
   (words_emb, docs_emb, docs_labels, docstrings, wordsmap)
 }

 val phraseintegrity_source: BufferedSource = scala.io.Source.fromFile(fetch("/benchmark/wiki_integrity.txt"))
 val phraseintegrity: Array[(String, Double)] = phraseintegrity_source.getLines().map(x => x.split('\t')).
   map(x => (x.last.replace(" ", "###"), x.head.toDouble)).toArray
 phraseintegrity_source.close()
}
