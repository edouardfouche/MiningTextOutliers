: '
/*
 * Copyright (C) 2020 Edouard Fouché, Yu Meng
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
#cd JoSE # Moving to the right folder
cd Spherical-Text-Embedding/src/

# dataset directory
dataset=$1 #../data/benchmark/nyt_1

# text file name; one document per line
text_file=$2 #nyt_1_seg_prep
extension=.txt

#make sphere_norm
make jose

margin=0.15
NEG=2
start=$SECONDS

# For the pretraining

./jose -train ${dataset}/${text_file}${extension} -load-emb ../output/emb_100_wiki_min_seg_prep_0.15_2_norm \
	-word-output ../output/emb_100_${text_file}_${margin}_${NEG}_norm_w.txt \
	-context-output ../output/emb_100_${text_file}_${margin}_${NEG}_norm_v.txt \
	-doc-output ../output/emb_100_${text_file}_${margin}_${NEG}_norm_d.txt \
	-size 100 -alpha 0.025 -margin ${margin} -window 10 -negative ${NEG} -sample 1e-3 \
	-min-count 10 -threads 20 -iter 10 # -joint 1 -binary 0

duration=$(( SECONDS - start ))
printf '\nRunning time %s\n' "$duration"
