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
benchmark=$1

green=`tput setaf 2`
reset=`tput sgr0`

echo ${green}==================AutoPhrase:${reset}

# Segment the benchmark corpus with AutoPhrase. Input ${benchmark}_cleaned, creates ${benchmark}_cleaned_seg
bash scripts/phrasal_segmentation.sh ../data/benchmark/${benchmark}/${benchmark}_cleaned.txt 
mv AutoPhrase/models/WIKIMIN/segmentation.txt data/benchmark/${benchmark}/${benchmark}_cleaned_seg.txt

echo ${green}======Formatting=tags:${reset}

# Replace the <phrase></phrase> tags with ### between words. Input ${benchmark}_cleaned_seg, creates ${benchmark}_cleaned_seg_prep
python scripts/phrasetags.py --dataset  data/benchmark/${benchmark}/ --in_file ${benchmark}_cleaned_seg.txt

echo ${green}======JoSE:${reset}

# Fine-train JoSE on the benchmark corpus
bash scripts/run_finetrain.sh ../../data/benchmark/${benchmark} ${benchmark}_cleaned_seg_prep

cp Spherical-Text-Embedding/output/emb_100_${benchmark}_cleaned_seg_prep_0.15_2_norm_d.txt data/benchmark/${benchmark}/${benchmark}_cleaned_seg_prep_emb_d.txt
cp Spherical-Text-Embedding/output/emb_100_${benchmark}_cleaned_seg_prep_0.15_2_norm_w.txt data/benchmark/${benchmark}/${benchmark}_cleaned_seg_prep_emb_w.txt

echo ${green}==================DONE:${reset}