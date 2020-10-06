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

class DataSet(val columns: Array[Array[Double]], val types: Array[String]) {
  // check that they all have same number of elements
  require{
    val l = columns.head.length
    columns.map(_.length).forall(_ == l)
  }

  // If one does not precise the type, simply consider them all numerical
  def this(columns: Array[Array[Double]]) = this(columns, columns.map(x => "n"))

  def toArray: Array[Array[Double]] = columns

  def take(nrows: Int): DataSet = new DataSet(columns.map(x => x.take(nrows)), types)

  def drop(nrows: Int): DataSet = new DataSet(columns.map(x => x.drop(nrows)), types)

  def head: Array[Double] = columns.map(x => x.head)

  def tail: DataSet = new DataSet(columns.map(x => x.tail), types)

  def get_type(x: Int): String = types(x)

  def apply(x: Int): Array[Double] = columns(x)

  def getrow(x: Int): Array[Double] = columns.map(y => y(x))
  def getcol(x: Int): Array[Double] = columns(x)

  def transpose: DataSet = {
    // note that we are loosing which types then
    new DataSet(columns.transpose)
  }
  def last: Array[Double] = columns.last
  def ncols: Int = columns.length
  def nrows: Int = columns.head.length
}

