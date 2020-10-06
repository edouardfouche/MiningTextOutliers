"""
/*
* Copyright (C) 2020 Yu Meng, Edouard Fouché
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
"""
from os.path import join

def format_labels(train_data, out_dir, out_file='./cleaned.txt'):
    f = open(join(out_dir, train_data))
    lines = f.readlines()
    output = open(join(out_dir, out_file), "w")
    categories = dict()
    current = 1
    for line in lines:
        cat = line.split("\t")[1].split("|")[0]
        if cat not in categories.keys():
            categories[cat] = current
            current += 1
        output.write("%s\t%s\n"%(cat, categories[cat]))
    return

if __name__ == "__main__":

    import argparse
    parser = argparse.ArgumentParser(description='pre',
                                     formatter_class=argparse.ArgumentDefaultsHelpFormatter)
    parser.add_argument('--dataset', default='')
    parser.add_argument('--in_file', default='dl.txt')
    parser.add_argument('--out_file', default='')
    args = parser.parse_args()
    dataset = args.dataset

    if args.out_file == '':
        if "." in args.in_file:
            out = args.in_file.split(".")[0] + "_prep." + args.in_file.split(".")[-1]
        else:
            out = args.in_file + "_prep"
    else:
    	out = args.out_file

    format_labels(args.in_file, dataset, out_file=out)