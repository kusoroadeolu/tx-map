Benchmark                                 Mode  Cnt         Score         Error  Units
CombinerBenchmark.arr_combiner_1thread   thrpt   10    103203.347 ±    6259.245  ops/s
CombinerBenchmark.arr_combiner_2threads  thrpt   10    178430.856 ±   11526.851  ops/s
CombinerBenchmark.arr_combiner_4threads  thrpt   10    305575.040 ±   34811.056  ops/s
CombinerBenchmark.arr_combiner_8threads  thrpt   10    580934.138 ±    6677.258  ops/s
CombinerBenchmark.sem_combiner_1thread   thrpt   10  17508179.250 ± 1143852.689  ops/s
CombinerBenchmark.sem_combiner_2threads  thrpt   10  14756836.253 ±  988253.841  ops/s
CombinerBenchmark.sem_combiner_4threads  thrpt   10  11915770.029 ±  239486.010  ops/s
CombinerBenchmark.sem_combiner_8threads  thrpt   10  10458455.635 ± 1084691.761  ops/s
CombinerBenchmark.unb_combiner_1thread   thrpt   10  18331700.488 ± 3587688.916  ops/s
CombinerBenchmark.unb_combiner_2threads  thrpt   10  17891108.667 ± 1292514.498  ops/s
CombinerBenchmark.unb_combiner_4threads  thrpt   10  14071634.268 ±  543557.869  ops/s
CombinerBenchmark.unb_combiner_8threads  thrpt   10  11519463.679 ±  743002.360  ops/s  //Benchmarks for all combiners
- Here array combiner used a spin wait retrying cas operations till its cell was not taken


Benchmark                                 Mode  Cnt       Score       Error  Units
CombinerBenchmark.arr_combiner_1thread   thrpt   10   99269.664 ±  6541.967  ops/s
CombinerBenchmark.arr_combiner_2threads  thrpt   10  174760.835 ± 14081.250  ops/s
CombinerBenchmark.arr_combiner_4threads  thrpt   10  296271.192 ± 34373.415  ops/s
CombinerBenchmark.arr_combiner_8threads  thrpt   10  561068.202 ± 16829.230  ops/s
//Even with the removal of the spin wait in the CAS loop we can see the numbers don't change that much

Benchmark                                 Mode  Cnt         Score         Error  Units
CombinerBenchmark.arr_combiner_1thread   thrpt   10  10547627.093 ± 3499840.722  ops/s
CombinerBenchmark.arr_combiner_2threads  thrpt   10  10178549.524 ± 1948961.888  ops/s
CombinerBenchmark.arr_combiner_4threads  thrpt   10   7783257.157 ± 1612621.688  ops/s
CombinerBenchmark.arr_combiner_8threads  thrpt   10   8470316.516 ± 1648970.201  ops/s