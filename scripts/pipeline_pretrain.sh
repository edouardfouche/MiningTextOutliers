: '
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
'
green=`tput setaf 2`
reset=`tput sgr0`

echo ${green}==================Preprocessing:${reset}

# Basic preprocessing of punctuation. Create ${benchmark}_cleaned
python scripts/clean.py --dataset data/wiki/ --in_file wiki_min.txt

echo ${green}==================AutoPhrase:${reset}

# Train AutoPhrase on Wikipedia corpus, extract `integrity' values
bash scripts/auto_phrase_wiki.sh 
cp AutoPhrase/models/WIKIMIN/AutoPhrase.txt data/benchmark/wiki_integrity.txt

# Segment Wikipedia corpus with AutoPhrase
bash scripts/phrasal_segmentation_wiki.sh
mv AutoPhrase/models/WIKIMIN/segmentation.txt data/wiki/wiki_min_seg.txt

echo ${green}======Formatting=tags:${reset}

# Replace the <phrase></phrase> tags with `###` between words
python scripts/phrasetags.py --dataset  data/wiki/ --in_file wiki_min_seg.txt 

echo ${green}======JoSE:${reset}

# Pre-train JoSE on segmented Wikipedia corpus
bash scripts/run_pretrain.sh

echo ${green}==================DONE:${reset}