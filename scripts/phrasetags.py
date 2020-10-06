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
import re
from os.path import join
from tqdm import tqdm


def read_file(data_dir, in_file='text.txt'):
    data = []
    filename = join(data_dir, in_file)
    with open(filename, 'r', encoding='utf-8', errors='ignore') as f:
        for line in f:
            data.append(line.strip())
    return data


def process_period(line):
    tokens = line.split(' ')
    for i, token in enumerate(tokens):
        separate = token.split('.')
        num_dots = len(separate) - 1
        if num_dots == 0:
            continue
        new_token = ''
        for j, letter in enumerate(separate):
            if j == len(separate) - 1:
                new_token += letter
            else:
                if len(letter) == 0 and len(separate[j+1]) > 0:
                    new_token += letter + '. '
                elif len(separate[j+1]) == 0 and len(letter) > 0:
                    new_token += letter + ' .'
                elif len(letter) > 1 or len(separate[j+1]) > 1:
                    new_token += letter + ' . '
                else:
                    new_token += letter + '.'
        tokens[i] = new_token
    ret = ' '.join(tokens)
    return ret


def dashit(match): 
    return "###".join(match.group(1).split(" ")) 

def clean_str(string):
    string = re.sub('<phrase>(.*?)</phrase>', dashit, string)
    return string.strip().lower()


def preprocess_doc(data):
    for i in tqdm(range(len(data))):
        s = data[i].strip()
        data[i] = clean_str(s)
    return data


def clean_file(train_data, out_dir, out_file='./cleaned.txt'):
    data = preprocess_doc(train_data)
    with open(join(out_dir, out_file), 'wt') as outfile:
        for line in data:
            outfile.write(line + '\n')
    return


if __name__ == "__main__":

    import argparse
    parser = argparse.ArgumentParser(description='pre',
                                     formatter_class=argparse.ArgumentDefaultsHelpFormatter)
    parser.add_argument('--dataset', default='')
    parser.add_argument('--in_file', default='nyt_wsdc_seg.txt')
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

    data = read_file(dataset, in_file=args.in_file)
    clean_file(data, dataset, out_file=out)
