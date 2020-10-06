: '
/*
 * Copyright (C) 2020 Jingbo Shang, Edouard Fouché
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
cd AutoPhrase # Moving to the right folder

MODEL=${MODEL:- "models/WIKIMIN"}
TEXT_TO_SEG=${TEXT_TO_SEG:-$1} #${TEXT_TO_SEG:-tmp/raw_tokenized_train.txt}  # ${TEXT_TO_SEG:-data/EN/DBLP.5K.txt}
HIGHLIGHT_MULTI=${HIGHLIGHT_MULTI:- 0.5}
HIGHLIGHT_SINGLE=${HIGHLIGHT_SINGLE:- 0.8}

SEGMENTATION_MODEL=${MODEL}/segmentation.model
TOKEN_MAPPING=${MODEL}/token_mapping.txt

ENABLE_POS_TAGGING=1
THREAD=10

green=`tput setaf 2`
reset=`tput sgr0`

echo ${green}===Compilation===${reset}

COMPILE=${COMPILE:- 1}
if [ $COMPILE -eq 1 ]; then
    bash compile.sh
fi

mkdir -p tmp
mkdir -p ${MODEL}

### END Compilation ###

echo ${green}===Tokenization===${reset}

TOKENIZER="-cp .:tools/tokenizer/lib/*:tools/tokenizer/resources/:tools/tokenizer/build/ Tokenizer"
TOKENIZED_TEXT_TO_SEG=tmp/tokenized_text_to_seg.txt
CASE=tmp/case_tokenized_text_to_seg.txt


echo -ne "Current step: Tokenizing input file...\033[0K\r"
time java $TOKENIZER -m direct_test -i $TEXT_TO_SEG -o $TOKENIZED_TEXT_TO_SEG -t $TOKEN_MAPPING -c N -thread $THREAD

LANGUAGE=`cat ${MODEL}/language.txt`
echo -ne "Detected Language: $LANGUAGE\033[0K\n"

### END Tokenization ###

echo ${green}===Part-Of-Speech Tagging===${reset}

if [ ! $LANGUAGE == "JA" ] && [ ! $LANGUAGE == "CN" ]  && [ ! $LANGUAGE == "OTHER" ]  && [ $ENABLE_POS_TAGGING -eq 1 ]; then
	RAW=tmp/raw_tokenized_text_to_seg.txt # TOKENIZED_TEXT_TO_SEG is the suffix name after "raw_"
	export THREAD LANGUAGE RAW
	bash ./tools/treetagger/pos_tag.sh
	mv tmp/pos_tags.txt tmp/pos_tags_tokenized_text_to_seg.txt
fi

POS_TAGS=tmp/pos_tags_tokenized_text_to_seg.txt

### END Part-Of-Speech Tagging ###

echo ${green}===Phrasal Segmentation===${reset}

if [ $ENABLE_POS_TAGGING -eq 1 ]; then
	time ./bin/segphrase_segment \
        --pos_tag \
        --thread $THREAD \
        --model $SEGMENTATION_MODEL \
		--highlight-multi $HIGHLIGHT_MULTI \
		--highlight-single $HIGHLIGHT_SINGLE
else
	time ./bin/segphrase_segment \
        --thread $THREAD \
        --model $SEGMENTATION_MODEL \
		--highlight-multi $HIGHLIGHT_MULTI \
		--highlight-single $HIGHLIGHT_SINGLE
fi

### END Segphrasing ###

echo ${green}===Generating Output===${reset}
java $TOKENIZER -m segmentation -i $TEXT_TO_SEG -segmented tmp/tokenized_segmented_sentences.txt -o ${MODEL}/segmentation.txt -tokenized_raw tmp/raw_tokenized_text_to_seg.txt -tokenized_id tmp/tokenized_text_to_seg.txt -c N

### END Generating Output for Checking Quality ###
