# A Unified Branch-Price-and-Cut Algorithm for Variable-Sized Bin Packing with Conflicts or Time Windows

This repository provides the Java source code, benchmark instances, and instance-level results for the branch-price-and-cut (BPC) algorithm described in the accompanying manuscript of the same title.

The implementation solves two one-dimensional variable-sized bin-packing problems:

- **VSBPPTW**: variable-sized bin packing with time windows. Two items conflict when their time windows are disjoint.
- **VSBPPC**: variable-sized bin packing with item conflicts.

The BPC algorithm combines column generation, 3-subset-row cuts, Ryan-Foster branching, primal diving, valid-bound early termination, reduced-cost column enumeration, and grouping for dense conflict graphs.

## Citation

If this repository is useful in your research, please cite the accompanying manuscript:

> Tao Zhang, Sunkanghong Wang, Lijun Wei, and Qiang Liu. "A Unified Branch-Price-and-Cut Algorithm for Variable-Sized Bin Packing with Conflicts or Time Windows." Manuscript.

The publication metadata will be added here when it becomes available.

## Repository layout

```text
BPC_For_VSBPPC_TW/
|-- Code/VSBPPC_Code/
|   `-- src/                    Java source code
|-- Instances/
|   |-- Instance_1/            VSBPPTW: Opt and Hem
|   `-- Instance_2/            VSBPPC: Set-1 and Set-2
|-- Results/
|   |-- all/                   Full BPC results
|   `-- no_*/                  Ablation results
|-- LICENSE
`-- README.md
```

The files under each `ReadFiles/` directory are manifests used by the batch runners. They list either instance directories or instance files, one path component per line.

## Requirements

The code was developed and tested with:

- Oracle OpenJDK 21.0.8
- IBM ILOG CPLEX Optimization Studio 12.10, including `cplex.jar` and its native library
- Lombok 1.18.38, with annotation processing enabled
- Apache Commons CSV 1.14.1, Commons IO 2.20.0, and Commons Codec 1.19.0
- IntelliJ IDEA Community Edition 2025.2

CPLEX and the other third-party dependencies are not distributed in this repository. The repository has no Maven or Gradle build descriptor, so the required JAR files must be attached to the project manually.

Every CPLEX model in the implementation sets `Threads = 1`. The archived experiments are therefore single-threaded.

## Running the solver

1. Open `Code/VSBPPC_Code` as a Java project in IntelliJ IDEA, select a Java 21 SDK, and mark `src` as a Sources Root.
2. Add `cplex.jar`, Lombok, Commons CSV, Commons IO, and Commons Codec to the module classpath. Enable annotation processing for Lombok.
3. Add the CPLEX native-library directory to the run configuration:

   ```text
   -Djava.library.path=<CPLEX_HOME>/cplex/bin/<platform-directory> -Xms4g -Xmx16g
   ```

4. Set the main class to `com.xiaopangxianren.main.InstanceLoder` and the working directory to `Code/VSBPPC_Code`.
5. The default output directories are under `Results/all/`. To preserve the archived results, change the output paths in `InstanceLoder.main()` before running the solver.
6. Run `InstanceLoder.main()`.

The supplied `main()` runs Opt, Hem, Set-1, and Set-2 sequentially and writes the results under `Results/all/`. The Set-1 and Set-2 runners create their nested output directories, while the Opt and Hem output directories must already exist; these directories are included in the repository.

### Platform note

`InstanceLoder.runningAllInstances()` currently constructs input paths with Windows backslashes. It runs directly on Windows. On macOS or Linux, replace the two backslash-based path expressions with `prefix.resolve(instanceType).resolve(fileName).toString()` before running a batch.

### Solver configuration

The five acceleration switches are defined in `Code/VSBPPC_Code/src/com/xiaopangxianren/classes/Parameter.java`.

| Switch | Component | Default |
| --- | --- | --- |
| `isUseSR_Cut` | 3-subset-row cuts | `true` |
| `isDiving` | Primal diving | `true` |
| `isUsePatternEnum` | Reduced-cost column enumeration | `true` |
| `EarlyTerminationEnable` | Valid-bound early termination | `true` |
| `isUseGrouping` | Dense-conflict grouping | `true` |

All five switches are `true` by default. This is the full configuration represented by `Results/all/`. To reproduce an ablation, disable the corresponding switch and use a separate output directory.

The batch time limits are fixed in `InstanceLoder.java`.

| Instances | Time limit per run |
| --- | ---: |
| Opt and Hem | 600 s |
| Set-1 and Set-2 with 100 items | 0.20 s |
| Set-1 and Set-2 with 200 items | 1.50 s |
| Set-1 and Set-2 with 500 items | 23.25 s |
| Set-1 and Set-2 with 1,000 items | 190.71 s |

The internal randomized heuristic uses the deterministic seed `929 * number_of_items * number_of_bin_types`.

## Benchmark instances

### VSBPPTW: Opt and Hem

The Opt and Hem families were introduced by [Liu et al. (2021)](https://doi.org/10.1016/j.cie.2021.107175). The Hem family extends the variable-sized bin-packing instances of [Haouari and Serairi (2009)](https://doi.org/10.1016/j.cor.2008.12.016) with generated time windows.

`Instances/Instance_1/opt/` contains 150 Opt instances. A file named `opt<q>_<c>_<r>.txt` has `3q` items, where `q` is one of 20, 40, 60, 80, and 100; `c = 0, 1, 2` denotes linear, concave, and convex bin costs; and `r = 0, ..., 9` is the replicate.

`Instances/Instance_1/hem/` contains 1,200 Hem instances. A file named `<n>-<c>-<r>-<w>` uses `n` items; `c = 1, 2, 3` denotes linear, concave, and convex bin costs; `r = 1, ..., 10` is the replicate; and `w` is one of 1, 2, 4, 8, 16, 32, 64, and 128.

Both data sets use the following whitespace-delimited format:

```text
<instance-name> [metadata ignored by the solver]
<number-of-bin-types>
<capacity-1> <cost-1>
...
<capacity-B> <cost-B>
<number-of-items>
<weight-1> <start-1> <end-1>
...
<weight-N> <start-N> <end-N>
```

Only the first token of the first line is read as the instance name. In particular, the reference optimum stored in the Opt metadata is not used by the solver. Time-window endpoints are inclusive: items `i` and `j` conflict exactly when `max(start_i, start_j) > min(end_i, end_j)`.

### VSBPPC: Set-1 and Set-2

The Set-1 and Set-2 benchmark families were introduced by [Ekici (2023)](https://doi.org/10.1016/j.ejor.2022.12.042).

`Instances/Instance_2/` contains 1,440 Set-1 instances and 480 Set-2 instances. Each Set-1 instance is solved with two bin-type configurations, giving 2,880 runs. Each Set-2 instance is solved with three cost structures, giving 1,440 runs.

The instance files use the following whitespace-delimited format:

```text
<number-of-items>
<item-id-1> <weight-1> [conflicting-item-id ...]
...
<item-id-N> <weight-N> [conflicting-item-id ...]
```

Item IDs must be consecutive integers from 1 to `N`, with one row per item in ID order. A conflict may be listed on either endpoint's row because the reader symmetrizes every listed edge. Empty conflict lists are allowed, and blank lines are ignored.

The VSBPPC files contain no bin data. The batch runner supplies the following capacities and costs from `InstanceLoder.java`:

| Configuration | Capacities | Costs |
| --- | --- | --- |
| Set-1, 3 types | `100, 120, 150` | `100, 120, 150` |
| Set-1, 5 types | `60, 80, 100, 120, 150` | `60, 80, 100, 120, 150` |
| Set-2, linear | `70, 100, 130, 160, 190, 220, 250` | `70, 100, 130, 160, 190, 220, 250` |
| Set-2, concave | `70, 100, 130, 160, 190, 220, 250` | `84, 100, 115, 127, 138, 149, 159` |
| Set-2, convex | `70, 100, 130, 160, 190, 220, 250` | `59, 100, 149, 203, 262, 327, 396` |

## Archived results

`Results/all/` contains 5,670 CSV files for the full BPC configuration: 150 Opt, 1,200 Hem, 2,880 Set-1, and 1,440 Set-2 runs. The VSBPPC directory suffix identifies the injected bin configuration: `_3` or `_5` for Set-1, and `_Linear`, `_Concavity`, or `_Convexity` for Set-2.

Each ablation directory contains the 1,350 Opt and Hem results used in the component analysis:

| Directory | Disabled component(s) |
| --- | --- |
| `no_src` | 3-subset-row cuts |
| `no_diving` | Primal diving |
| `no_enu` | Reduced-cost column enumeration |
| `no_earlyTermination` | Valid-bound early termination |
| `no_grouping` | Dense-conflict grouping |
| `no_all` | All five components |

### CSV structure

Every `*_results.csv` file describes one run. Lines 1 to 13 are a one-field metadata summary, lines 14 and 15 are blank, line 16 is the tabular header, and line 17 is the instance record. An optional pandas reader can therefore load the tabular part with:

```python
import pandas as pd

result = pd.read_csv("Results/all/opt/opt20_0_0.txt_results.csv", skiprows=15)
```

The 22 tabular columns are:

| Column | Meaning |
| --- | --- |
| `instance` | Instance name without the final `.txt` suffix. |
| `UB`, `LB` | Final integer upper and lower bounds on the objective value. |
| `Gap` | Final gap in percent. It is `100 * (UB - LB) / UB` for VSBPPTW and `100 * (UB - LB) / LB` for VSBPPC. |
| `IsOpt` | `true` if optimality was proved before the time limit. |
| `RootEnu_IsOpt` | `true` if root-node column enumeration and its final restricted integer solve proved optimality. |
| `GenNodeCnt` | Nodes generated by the complete BPC search, including the root and excluding diving nodes. |
| `ExpNodeCnt` | Nonroot nodes processed by the complete BPC search. |
| `ExpDivingNodeCnt` | Nodes processed by the diving search. |
| `RmpCnt`, `PricingCnt` | Numbers of restricted-master solves and pricing calls. |
| `PatternEnuCnt` | Number of root-node column-enumeration attempts, not the number of enumerated columns. |
| `TotalTime` | Total wall-clock solution time in milliseconds. |
| `RmpTime` | Cumulative restricted-master solution time in milliseconds. |
| `PricingTime` | Cumulative pricing-stage time in milliseconds. |
| `DivingTime` | Wall-clock time spent in the diving phase in milliseconds. |
| `FindPatternEnuTime` | Time used to identify columns eligible for enumeration, in milliseconds. |
| `PatternEnuTime` | Total column-enumeration phase time, including column identification and the final restricted integer solve, in milliseconds. |
| `AvgRmpTime`, `AvgPricingTime` | Corresponding cumulative time divided by `RmpCnt` or `PricingCnt`, using integer milliseconds. |
| `AvgFindPatternEnuTime`, `AvgPatternEnuTime` | Corresponding cumulative time divided by `PatternEnuCnt`, using integer milliseconds. |

The timing columns are measured by overlapping algorithm phases and should not be added together. An average is recorded as zero when its denominator is zero.

## License

The source code is released under the [MIT License](LICENSE). IBM ILOG CPLEX and all other third-party dependencies remain subject to their own licenses.

## Contact

For questions, please feel free to contact [villagerwei@gdut.edu.cn](mailto:villagerwei@gdut.edu.cn) or [zt1174882482@outlook.com](mailto:zt1174882482@outlook.com).
