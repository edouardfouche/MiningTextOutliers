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
package io.github.edouardfouche.index

import java.util

import de.lmu.ifi.dbs.elki.data.{DoubleVector, NumberVector}
import de.lmu.ifi.dbs.elki.data.`type`.TypeUtil
import de.lmu.ifi.dbs.elki.database.StaticArrayDatabase
import de.lmu.ifi.dbs.elki.database.ids.{DBIDFactory, DoubleDBIDListIter, KNNList}
import de.lmu.ifi.dbs.elki.database.query.knn.KNNQuery
import de.lmu.ifi.dbs.elki.database.relation.Relation
import de.lmu.ifi.dbs.elki.datasource.ArrayAdapterDatabaseConnection
import de.lmu.ifi.dbs.elki.distance.distancefunction.minkowski.EuclideanDistanceFunction
import de.lmu.ifi.dbs.elki.index.tree.spatial.rstarvariants.rstar.RStarTreeFactory
import de.lmu.ifi.dbs.elki.persistent.AbstractPageFileFactory
import de.lmu.ifi.dbs.elki.utilities.ELKIBuilder
import io.github.edouardfouche.preprocess.DataSet
import de.lmu.ifi.dbs.elki.distance.distancefunction.CosineDistanceFunction
import de.lmu.ifi.dbs.elki.logging.CLISmartHandler

case class ElkiKNN(dataset: DataSet, usecache: Boolean = true, distance: String = "euclidean") {
  val log = new CLISmartHandler()
  val ncols: Int = dataset.ncols
  val nrows: Int = dataset.nrows

  val factory: DBIDFactory = DBIDFactory.FACTORY
  val dbc = new ArrayAdapterDatabaseConnection(dataset.toArray.transpose, null, 0)
  // Following the recommendations from Schubert at https://stackoverflow.com/questions/32741510/how-to-index-with-elki-optics-clustering
  //val bulkSplitID = new OptionID("spatial.bulkstrategy", "The class to perform the bulk split with.")
  val indexfactory: RStarTreeFactory[_ <: NumberVector] = new ELKIBuilder[RStarTreeFactory[_ <: NumberVector]](classOf[RStarTreeFactory[_ <: NumberVector]]).`with`(
    AbstractPageFileFactory.Parameterizer.PAGE_SIZE_ID, 1024 * ncols / 2.0).build()//.`with`(
   // bulkSplitID, classOf[SortTileRecursiveBulkSplit]).build()

  // If you have large query results, a larger page size can be better.
  //(AbstractPageFileFactory.Parameterizer.PAGE_SIZE_ID, 1024 * this.ncols / 2.0).with // Use bulk loading, for better performance.
  //(RStarTreeFactory.Parameterizer.BULK_SPLIT_ID, classOf[SortTileRecursiveBulkSplit]).build
  //this.dataBase = new StaticArrayDatabase(dbc, null); // Create a database

  //val indexfactory: RStarTreeFactory[_] = new ELKIBuilder[RStarTreeFactory[_ <: NumberVector]](classOf[RStarTreeFactory[_ <: NumberVector]]).`with` //
  // If you have large query results, a larger page size can be better.
  //(AbstractPageFileFactory.Parameterizer.PAGE_SIZE_ID, 1024 * this.DIMENSIONS / 2.0).`with` // Use bulk loading, for better performance.
  //(RStarTreeFactory.Parameterizer.BULK_SPLIT_ID, classOf[SortTileRecursiveBulkSplit]).build

  val database = new StaticArrayDatabase(dbc, util.Arrays.asList(indexfactory))
  database.initialize()

  //MaximumDistanceFunction.STATIC
  val relation: Relation[NumberVector] = database.getRelation(TypeUtil.NUMBER_VECTOR_FIELD)
  val knnq: KNNQuery[NumberVector] = if(distance == "euclidean") relation.getKNNQuery(EuclideanDistanceFunction.STATIC)
  else relation.getKNNQuery(CosineDistanceFunction.STATIC)

  val cache: scala.collection.mutable.Map[List[Double], KNNList] =
    scala.collection.mutable.Map[List[Double], KNNList]()

  def fillcache(tocache: Array[Array[Double]], max: Int = nrows-1): Unit = {
    for{obj <- tocache} {
      cache(obj.toList) = knnq.getKNNForObject(new DoubleVector(obj), max+1)
    }
  }

  def getKNNValuesForObject(obj: Array[Double], k: Int): Array[Array[Double]] = {
    assert(obj.length == dataset.ncols, s"Obj must have the same number of dimensions as dataset, ${obj.length} != ${dataset.ncols}") // TODO: I don't understand what is the result in case this does not hold ?
    if(usecache) {
      val kNNeighbours = new Array[Array[Double]](k)
      val result: KNNList = cache.getOrElseUpdate(obj.toList, knnq.getKNNForObject(new DoubleVector(obj), nrows))
      val niter: DoubleDBIDListIter = result.iter
      var i = 0
      while(i < k && niter.valid) {
        kNNeighbours(i) = relation.get(niter).toArray
        niter.advance
        i += 1
      }
      kNNeighbours
    } else {
      val kNNeighbours = new Array[Array[Double]](k)
      val result: KNNList = knnq.getKNNForObject(new DoubleVector(obj), k)
      val niter: DoubleDBIDListIter = result.iter
      var i = 0
      while(niter.valid) {
        kNNeighbours(i) = relation.get(niter).toArray
        niter.advance
        i += 1
      }
      kNNeighbours
    }
  }

  /**
   * @return
   */
  def getAllKNN(k: Int): Array[Array[Array[Double]]] = {
    val AllkNNeighbours: Array[Array[Array[Double]]] = (1 to nrows).map(x => (1 to k).map(y => Array.fill[Double](ncols)(0.0)).toArray).toArray
    val iter = relation.iterDBIDs

    var j = 0
    while (iter.valid) {
      val result = knnq.getKNNForDBID(iter, k)
      val kNNeighbours = (1 to k).map(y => Array.fill[Double](ncols)(0.0)).toArray
      val niter = result.iter

      var i = 0
      while (niter.valid) {
        kNNeighbours(i) = relation.get(niter).toArray
        niter.advance
        i += 1
      }
      AllkNNeighbours(j) = kNNeighbours

      iter.advance
      j += 1
    }
    AllkNNeighbours
  }

  def getAllKNNDistances(k: Int): Array[Array[Double]] = {
    val Alldistances: Array[Array[Double]] = (1 to nrows).map(x => (1 to k).map(y => 0.0).toArray).toArray
    val iter = relation.iterDBIDs

    var j = 0
    while (iter.valid) {
      val result = knnq.getKNNForDBID(iter, k)
      val kNNeighbours = (1 to k).map(y => Array.fill[Double](ncols)(0.0)).toArray
      val niter = result.iter

      var i = 0
      while (niter.valid) {
        kNNeighbours(i) = relation.get(niter).toArray
        niter.advance
        i += 1
      }
      // Get the object first
      val value = relation.get(iter).toArray
      val distances = new Array[Double](ncols)

      var ii = 0
      while (ii < kNNeighbours(0).length) {
        distances(ii) = -1
        for (p <- kNNeighbours) {
          val distance = Math.abs(p(ii) - value(ii))
          if (distance > distances(ii)) distances(ii) = distance
        }
        ii += 1;
      }
      Alldistances(j) = distances
      iter.advance
      j += 1
    }
    Alldistances
  }

  /**
   * get the distance to the kNN in one Dimension
   *
   * @param norm the norm (?)
   * @return the distance to the kNN
   */
  def getAllKDistance(k: Int, norm: (Array[Double], Array[Double]) => Double): Array[Double] = {
    val Alldistances = new Array[Double](nrows)
    val iter = this.relation.iterDBIDs

    var j = 0
    while (iter.valid) {
      val result = this.knnq.getKNNForDBID(iter, k)
      val kNNs = new Array[Array[Double]](result.size)
      val niter = result.iter

      var i = 0
      while (niter.valid) {
        kNNs(i) = this.relation.get(niter).toArray
        niter.advance
        i += 1
      }
      val value = this.relation.get(iter).toArray
      var distance = .0
      val k_next_neighbour = new Array[Double](value.length)
      var m = 0
      while (m < value.length) {
        k_next_neighbour(m) = kNNs(kNNs.length - 1)(m)
        m += 1
      }
      distance = norm(value, k_next_neighbour)
      Alldistances(j) = distance
      iter.advance
      j += 1
    }
    Alldistances
  }
}
