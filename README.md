# Mining Text Outliers in Document Directories

Welcome to the supplementary material for our paper:

- Edouard Fouché, Yu Meng, Fang Guo, Honglei Zhuang, Klemens Böhm, and Jiawei Han, "Mining Text Outliers in Document Directories", in Proc. 2020 IEEE Int. Conf. on Data Mining (ICDM'20), Nov. 2020

This repository contains the code and data to reproduce our results. 
We release it under the AGPLv3 license. Please see the [LICENSE.md](LICENSE.md) file. 

## Setup

**Requirements** : GCC compiler, Python 3 (csv, numpy, tqdm)

### Clone (or fork first, as you wish)

```bash
git clone https://github.com/edouardfouche/MiningTextOutliers.git
cd MiningTextOutliers
```

### Get the data and external libraries 

- Get the Wikipedia corpus, place it in `data/`:

```bash
wget https://www.ipd.kit.edu/MiningTextOutliers/wiki.zip
unzip wiki.zip -d data/ 
rm wiki.zip
```

- Get the benchmark data sets, place them in `data/`:

```bash
wget https://www.ipd.kit.edu/MiningTextOutliers/benchmark.zip
unzip benchmark.zip -d data/
rm benchmark.zip
```

- Get AutoPhrase: 

```bash
git clone https://github.com/shangjingbo1226/AutoPhrase.git
```

- Get JoSE: 

```bash
git clone  https://github.com/yumeng5/Spherical-Text-Embedding.git
```

### Pre-training on the external corpus 

First, we need to pre-train the joint text embedding with the Wikipedia corpus. The script below does the following: 
- Basic preprocessing of punctuation.
- Train AutoPhrase on Wikipedia corpus.
- Segment the Wikipedia corpus with AutoPhrase.
- Replace the `<phrase></phrase>` tags with `###` between words.
- Pre-train the joint spherical embedding (JoSE) on the segmented Wikipedia corpus.

```bash
bash scripts/pipeline_pretrain.sh
```

See the file `scripts/pipeline_pretrain.sh` for more details. 
This step can take some time on a standard laptop (about half a day). 
In comparison, the follow-up steps below are much shorter. 

### Fine-train for each benchmark

For each benchmark corpus, the script below does the following: 
- Segment the benchmark corpus with AutoPhrase. 
- Replace the `<phrase></phrase>` tags with ### between words.
- Fine-train the joint spherical embedding (JoSE) on the benchmark corpus.

The `data/benchmark/` folder provide the following benchmark corpora, as in our paper:  
`arxiv_15`, `arxiv_25`, `arxiv_35`, `arxiv_45`, `arxiv_55`, `arxiv_54`, `arxiv_53`, `arxiv_52`, `arxiv_51`, `nyt_1`, `nyt_2`, `nyt_5`, `nyt_10`, `nyt_20`, `nyt_50`. 

The benchmark `arxiv_cs` is the set of all papers in category `CS` (Computer Science), that we use in Section VII.C. 

```bash
bash scripts/pipeline_finetrain.sh <benchmark>
```

See the file `scripts/pipeline_finetrain.sh` for more details. 

### Using your own benchmark

You are welcome to try out our approach with your own benchmark data set. Please observe the structure of our own benchmarks. 
For your convenience, we provide a few scripts `scripts/clean.py` and  `scripts/formatlabels.py` for basic cleaning of text and formatting of labels. 
You may also use a larger Wikipedia corpus for pre-training, but this may take more time. 

## Reproduce the experiments

### Build

**Requirements** : ([Oracle JDK 8](https://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html)
or [OpenJDK 8](http://openjdk.java.net/install/)) and [sbt](https://www.scala-sbt.org/1.0/docs/Setup.html)

The project is built with sbt (version 1.2.8). You can compile, package or run the project as follows:

```
sbt compile
sbt package 
sbt "run <arguments>"
```

In case you need to adjust the amount of memory assigned to the JVM, you can set the `javaOptions` (`-Xmx` and `-Xms`) in `build.sbt`.

### Optional: Install BLAS (Basic Linear Algebra Subroutines)

Speed-up linear algebra operations (like dot- and cross-product).

```bash
# See: https://github.com/fommil/netlib-java#linux
sudo apt-get install libatlas3-base libopenblas-base
sudo update-alternatives --config libblas.so
sudo update-alternatives --config libblas.so.3
sudo update-alternatives --config liblapack.so
sudo update-alternatives --config liblapack.so.3
```

### Run the experiments

Evaluate the Type O/M recall and precision w.r.t. parameters.

```bash
sbt "run com.edouardfouche.experiments.Entropy"
```

Ablation analysis.

```bash
sbt "run com.edouardfouche.experiments.Ablation"
```

Evaluate our approach, kj-NN.

```bash
sbt "run com.edouardfouche.experiments.Evaluate_KJNN"
```

Evaluate other approaches (see below).

```bash
sbt "run com.edouardfouche.experiments.Evaluate_RSHash"
sbt "run com.edouardfouche.experiments.Evaluate_LOF"
sbt "run com.edouardfouche.experiments.Evaluate_AvgNegCosSim"
```

Exploit our approach, as in Section VII.C.

```bash
sbt "run com.edouardfouche.experiments.Exploit_KJNN"
```

### Visualize, reproduce the figures

**Requirements** : Jupyter notebooks, matplotlib, seaborn

In `visualize/`, we provide a set of `jupyter notebooks` to explore the results from the experiments and reproduce the figures from our publication.

- `Entropy.ipynb`: Reproduce Figure 3 and 4. 
- `Ablation.ipynb`: Get the quantitative results from our ablation analysis (Table I, II, IV, V, VI).
- `Evaluate_KJNN.ipynb`: Get the quantitative results from kj-NN.
- `Evaluate_RSHash.ipynb`: Get the  quantitative results from RS-Hash (Table I, V).
- `Evaluate_LOF.ipynb`: Get the  quantitative results from RS-Hash (Table I, V).
- `Evaluate_AvgNegCosSim.ipynb`: Get the  quantitative results from ANCS/k-ANCS (Table I, V).
- `Runtime.ipynb`: Compare the runtime of each approach (Figure 5).

Remark: Due to a small bug, the results of RS-Hash were slightly underestimated in the paper. 
However, the experiments show that they are still largely inferior to our proposed method (kj-NN). 
In our early developments, we called Type M = Type B and Type O = Type A outliers, so these may appear in implementation details. 

### Baselines

`RS-Hash`, `LOF`, `ANCS` and `k-ANCS` are included. For the other approaches, with used the following sources: 

- CVDD: https://github.com/lukasruff/CVDD-PyTorch
- W-CNN, AT-RNN, RCNN, VD-CNN: https://github.com/dongjun-Lee/text-classification-models-tf
- TONMF: https://github.com/ramkikannan/outliernmf
- VMF-Q: Code kindly shared by Honglei Zhuang (https://github.com/hongleizhuang)

## Acknowledgment

This work was supported by the DFG Research Training Group 2153: `Energy Status Data -- Informatics Methods for its Collection, Analysis and Exploitation', the German Federal Ministry of Education and Research (BMBF) via Software Campus (01IS17042) and sponsored in part by US DARPA KAIROS Program No. FA8750-19-2-1004 and SocialSim Program No.  W911NF-17-C-0099, National Science Foundation IIS 16-18481, IIS 17-04532, and IIS-17-41317, and DTRA HDTRA11810026. 