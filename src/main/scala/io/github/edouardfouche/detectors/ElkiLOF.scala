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

import de.lmu.ifi.dbs.elki.algorithm.outlier.lof.LOF
import de.lmu.ifi.dbs.elki.database.StaticArrayDatabase
import de.lmu.ifi.dbs.elki.index.tree.spatial.rstarvariants.strategies.bulk.SortTileRecursiveBulkSplit
import de.lmu.ifi.dbs.elki.utilities.optionhandling.OptionID
import de.lmu.ifi.dbs.elki.datasource.ArrayAdapterDatabaseConnection
import de.lmu.ifi.dbs.elki.distance.distancefunction.minkowski

// For the indexing
import java.util.Arrays

import de.lmu.ifi.dbs.elki.index.tree.spatial.rstarvariants.rstar.RStarTreeFactory

import scala.collection.mutable.ListBuffer
import de.lmu.ifi.dbs.elki.data.NumberVector
import de.lmu.ifi.dbs.elki.persistent.AbstractPageFileFactory
import de.lmu.ifi.dbs.elki.utilities.ELKIBuilder

/**
  * A Scala wrapper for the LOF implementation from ELKI.
  *
  * @note Using the index does not bring a big advantage in most cases. That's why "index" is false by default.
  * Sometimes complains that the page size is too small and performance are worst.
  */
case class ElkiLOF(var k: Int, index: Boolean = true) extends FullSpaceOutlierDetector {
  val id = s"ElkiLOF-$k"

  def setkj(newk: Int, newj: Int): Unit = {
    k = newk
  }

  /**
    * Compute the LOF for each element of a given array of instances.
    *
    * @param instances instances (rows) of a data set.
    * @return An arrax of 2-Tuple. The first element is the index, the second is the LOF.
    */
  def computeScores(instances: Array[Array[Double]]): Array[(Int, Double)] = {
    val K = k.min(instances.length-1) // k must be lower than the total number of instances !

    val distance = minkowski.EuclideanDistanceFunction.STATIC
    val lof = new LOF(K, distance)

    // Set startid to 0, very important to avoid AbortException, see https://github.com/elki-project/elki/issues/40
    // https://github.com/elki-project/elki/blob/master/addons/tutorial/src/main/java/tutorial/javaapi/GeoIndexing.java
    // Following the recommendations from Schubert at https://stackoverflow.com/questions/32741510/how-to-index-with-elki-optics-clustering
    val db = if (index) {
      val bulkSplitID = new OptionID("spatial.bulkstrategy", "The class to perform the bulk split with.")
      val indexfactory: RStarTreeFactory[_ <: NumberVector] = new ELKIBuilder[RStarTreeFactory[_ <: NumberVector]](classOf[RStarTreeFactory[_ <: NumberVector]]).`with`(
        AbstractPageFileFactory.Parameterizer.PAGE_SIZE_ID, 1024 * instances(0).length / 2.0).`with`(
        bulkSplitID, classOf[SortTileRecursiveBulkSplit]).build()
      //.`with`(
      //RStarTreeFactory.Parameterizer.BULK_SPLIT_ID, classOf[SortTileRecursiveBulkSplit]).build
      // If you have large query results, a larger page size can be better.
      //val indexfactory = new ELKIBuilder[RStarTreeFactory[_ <: Array[Double]]](classOf[RStarTreeFactory[_ <: Array[Double]]]).`with`(
      //  AbstractPageFileFactory.Parameterizer.PAGE_SIZE_ID, 512).build //
      val dbc = new ArrayAdapterDatabaseConnection(instances, null, 0) // Adapter to load data from an existing array.
      new StaticArrayDatabase(dbc, Arrays.asList(indexfactory)) // Create a database
    } else {
      val dbc = new ArrayAdapterDatabaseConnection(instances, null, 0) // Adapter to load data from an existing array.
      new StaticArrayDatabase(dbc, null) // Create a database
    }

    db.initialize()
    val result = lof.run(db).getScores()

    var scoreList = new ListBuffer[Double]()
    val DBIDs = result.iterDBIDs()
    while ( {
      DBIDs.valid
    }) {
      scoreList += result.doubleValue(DBIDs)
      DBIDs.advance
    }
    scoreList

    val corrected = scoreList.map {
      case d if d.isNaN => 1.0 // Or whatever value you'd prefer.
      case d if d.isNegInfinity => 1.0 // Or whatever value you'd prefer.
      case d if d.isPosInfinity => 1.0 // Or whatever value you'd prefer.
      case d => d
    }
    corrected.toArray.zipWithIndex.map(x => (x._2, x._1))
  }
}
