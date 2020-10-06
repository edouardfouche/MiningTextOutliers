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

import java.io.{File, FileWriter}

import com.typesafe.scalalogging.LazyLogging
import io.github.edouardfouche.preprocess.DataSet
import io.github.edouardfouche.utils
import org.slf4j.MDC

/**
  * General trait with functions for our experiments
  */
trait Experiment extends LazyLogging {

  // output formatting
  val output_folder: String = System.getProperty("user.dir")
  val master_experiment_folder: String = output_folder concat "/" concat "experiments"
  utils.createFolderIfNotExisting(master_experiment_folder)
  val formatter = new java.text.SimpleDateFormat("yyy-MM-dd-HH-mm")
  val dirname: String = s"${formatter.format(java.util.Calendar.getInstance().getTime)}_${this.getClass.getSimpleName.init}_"
  val experiment_folder: String = master_experiment_folder concat "/" concat dirname
  val summaryPath = experiment_folder + "/" + this.getClass.getSimpleName.init + ".csv"

  MDC.put("path", s"$experiment_folder/${this.getClass.getSimpleName.init}.log")
  info(s"Logging path: $experiment_folder/${this.getClass.getSimpleName.init}.log")

  info(s"${formatter.format(java.util.Calendar.getInstance().getTime)} - Starting the experiment ${this.getClass.getSimpleName.init}\n")
  utils.createFolderIfNotExisting(experiment_folder)

  info(s"Started on: ${java.net.InetAddress.getLocalHost.getHostName}")

  def run(): Unit

  def info(s: String): Unit = {
    // Repeat the MDC so that we are sure that, even if we are in a subprocess, that the information will be logged centrally
    MDC.put("path", s"$experiment_folder/${this.getClass.getSimpleName.init}.log")
    logger.info(s)
  }

  def subsample(docs: DataSet, documents: Array[String], labels: Array[Double], samplesize: Int, balance: Boolean = true): (DataSet, Array[String], Array[Double]) = {
    if(!balance) {
      val selected = scala.util.Random.shuffle(labels.indices.toList).take(samplesize)
      val rows: Array[Array[Double]] = docs.columns.transpose
      val newdocs = new DataSet(selected.map(x => rows(x)).toArray.transpose)
      val newdocuments = selected.map(x => documents(x)).toArray
      val newlabels = selected.map(x => labels(x)).toArray
      (newdocs, newdocuments, newlabels)
    } else {
      val subsamplesize = math.floor(samplesize.toDouble / labels.distinct.length.toDouble).toInt
      val groups = labels.zipWithIndex.groupBy(_._1).mapValues(_.map(_._2)).values
      val selected = groups.flatMap(x => scala.util.Random.shuffle(x.toList).take(subsamplesize))
      val rows: Array[Array[Double]] = docs.columns.transpose
      val newdocs = new DataSet(selected.map(x => rows(x)).toArray.transpose)
      val newdocuments = selected.map(x => documents(x)).toArray
      val newlabels = selected.map(x => labels(x)).toArray
      (newdocs, newdocuments, newlabels)
    }
  }

  def corruptlabels(labels: Array[Double], noise: Double): Array[Double] = {
    if(noise == 0) return labels
    val classes = labels.distinct.toSet
    if(classes.size <= 1) labels // nothing to corrupt !
    else {
      val nbtocorrupt = (labels.length * noise).toInt
      val tocorrupt = scala.util.Random.shuffle(labels.indices.toList).take(nbtocorrupt)
      val corrupted = labels.clone
      tocorrupt.foreach(x => {
        corrupted(x) = scala.util.Random.shuffle((classes - corrupted(x)).toList).head
      })
      info(s"$nbtocorrupt labels corrupted. Example: ${labels(tocorrupt.head)} -> ${corrupted(tocorrupt.head)} at position ${tocorrupt.head}")
      corrupted
    }
  }

  case class ExperimentSummary(attributes: List[String]) {
    //var results: List[(String, Any)] = List()
    val results: scala.collection.mutable.Map[String, Any] = scala.collection.mutable.Map[String, Any]()

    //def add(name: String, v: Any): Unit = results = results :+ (name, v)
    def add(name: String, v: Any): Unit = {
      results(name) = v
    }

    def write(path: String): Unit = {
      synchronized {
        if(!new File(path).exists) { // write the header
          val fileA = new File(path)
          val fwA = new FileWriter(fileA, true)
          fwA.write(getHeader)
          fwA.flush()
          fwA.close()
        }
        val fileA = new File(path)
        val fwA = new FileWriter(fileA, true) // append set to true
        fwA.write(this.toString) // this is the string
        fwA.flush()
        fwA.close()
      }
    }

    override def toString: String = {
      (attributes.map(x => results.getOrElse(x, "NULL").toString) mkString ",") + "\n"
    }

    def getHeader: String = (attributes mkString ",") + "\n"
  }
}
