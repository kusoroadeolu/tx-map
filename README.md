[# Transactional Maps
## Mvcc Transactional Map
This transactional map uses MVCC to allow readers and writers to operate without blocking each other. Readers snapshot the map state at transaction start and always see a consistent view, writes are versioned per key and old versions are pruned once no active transaction can reach them. This map promises SNAPSHOT isolation guarantees.

# Benchmarks
## Zipfian Bench
### No Retries
```java
Benchmark                                              (versionChainType)   Mode  Cnt         Score        Error  Units
ZipfianBenchmark.highSkew_readHeavy_1thread                         queue  thrpt   15   2949543.225 ± 605058.541  ops/s
ZipfianBenchmark.highSkew_readHeavy_1thread:aborts                  queue  thrpt   15           ≈ 0                   #
ZipfianBenchmark.highSkew_readHeavy_1thread:commits                 queue  thrpt   15  44366039.000                   #
ZipfianBenchmark.highSkew_readHeavy_1thread                           nav  thrpt   15   1502361.649 ± 181065.109  ops/s
ZipfianBenchmark.highSkew_readHeavy_1thread:aborts                    nav  thrpt   15           ≈ 0                   #
ZipfianBenchmark.highSkew_readHeavy_1thread:commits                   nav  thrpt   15  22555685.000                   #
ZipfianBenchmark.highSkew_readHeavy_2threads                        queue  thrpt   15   3450096.540 ± 247576.046  ops/s
ZipfianBenchmark.highSkew_readHeavy_2threads:aborts                 queue  thrpt   15     69704.000                   #
ZipfianBenchmark.highSkew_readHeavy_2threads:commits                queue  thrpt   15  51722355.000                   #
ZipfianBenchmark.highSkew_readHeavy_2threads                          nav  thrpt   15   2027477.224 ± 105458.277  ops/s
ZipfianBenchmark.highSkew_readHeavy_2threads:aborts                   nav  thrpt   15     54365.000                   #
ZipfianBenchmark.highSkew_readHeavy_2threads:commits                  nav  thrpt   15  30436189.000                   #
ZipfianBenchmark.highSkew_readHeavy_4threads                        queue  thrpt   15   4523013.425 ± 159685.613  ops/s
ZipfianBenchmark.highSkew_readHeavy_4threads:aborts                 queue  thrpt   15    236593.000                   #
ZipfianBenchmark.highSkew_readHeavy_4threads:commits                queue  thrpt   15  67689178.000                   #
ZipfianBenchmark.highSkew_readHeavy_4threads                          nav  thrpt   15   2760282.492 ± 158198.932  ops/s
ZipfianBenchmark.highSkew_readHeavy_4threads:aborts                   nav  thrpt   15    190749.000                   #
ZipfianBenchmark.highSkew_readHeavy_4threads:commits                  nav  thrpt   15  41264618.000                   #
ZipfianBenchmark.highSkew_readHeavy_8threads                        queue  thrpt   15   5543931.259 ± 605323.484  ops/s
ZipfianBenchmark.highSkew_readHeavy_8threads:aborts                 queue  thrpt   15    661132.000                   #
ZipfianBenchmark.highSkew_readHeavy_8threads:commits                queue  thrpt   15  83774318.000                   #
ZipfianBenchmark.highSkew_readHeavy_8threads                          nav  thrpt   15   3198280.023 ± 316016.939  ops/s
ZipfianBenchmark.highSkew_readHeavy_8threads:aborts                   nav  thrpt   15    538522.000                   #
ZipfianBenchmark.highSkew_readHeavy_8threads:commits                  nav  thrpt   15  48805059.000                   #
ZipfianBenchmark.highSkew_writeHeavy_1thread                        queue  thrpt   15   2387028.597 ± 571382.835  ops/s
ZipfianBenchmark.highSkew_writeHeavy_1thread:aborts                 queue  thrpt   15           ≈ 0                   #
ZipfianBenchmark.highSkew_writeHeavy_1thread:commits                queue  thrpt   15  35842655.000                   #
ZipfianBenchmark.highSkew_writeHeavy_1thread                          nav  thrpt   15   1082192.894 ±  97205.144  ops/s
ZipfianBenchmark.highSkew_writeHeavy_1thread:aborts                   nav  thrpt   15           ≈ 0                   #
ZipfianBenchmark.highSkew_writeHeavy_1thread:commits                  nav  thrpt   15  16410745.000                   #
ZipfianBenchmark.highSkew_writeHeavy_2threads                       queue  thrpt   15   3156547.488 ± 238160.889  ops/s
ZipfianBenchmark.highSkew_writeHeavy_2threads:aborts                queue  thrpt   15    373998.000                   #
ZipfianBenchmark.highSkew_writeHeavy_2threads:commits               queue  thrpt   15  47082464.000                   #
ZipfianBenchmark.highSkew_writeHeavy_2threads                         nav  thrpt   15   1394444.818 ± 145291.222  ops/s
ZipfianBenchmark.highSkew_writeHeavy_2threads:aborts                  nav  thrpt   15    266347.000                   #
ZipfianBenchmark.highSkew_writeHeavy_2threads:commits                 nav  thrpt   15  21149641.000                   #
ZipfianBenchmark.highSkew_writeHeavy_4threads                       queue  thrpt   15   3818498.807 ± 153931.852  ops/s
ZipfianBenchmark.highSkew_writeHeavy_4threads:aborts                queue  thrpt   15   1387376.000                   #
ZipfianBenchmark.highSkew_writeHeavy_4threads:commits               queue  thrpt   15  56177520.000                   #
ZipfianBenchmark.highSkew_writeHeavy_4threads                         nav  thrpt   15   1785457.350 ± 200900.647  ops/s
ZipfianBenchmark.highSkew_writeHeavy_4threads:aborts                  nav  thrpt   15    765630.000                   #
ZipfianBenchmark.highSkew_writeHeavy_4threads:commits                 nav  thrpt   15  26150199.000                   #
ZipfianBenchmark.highSkew_writeHeavy_8threads                       queue  thrpt   15   3802122.606 ± 477946.109  ops/s
ZipfianBenchmark.highSkew_writeHeavy_8threads:aborts                queue  thrpt   15   3091554.000                   #
ZipfianBenchmark.highSkew_writeHeavy_8threads:commits               queue  thrpt   15  56717661.000                   #
ZipfianBenchmark.highSkew_writeHeavy_8threads                         nav  thrpt   15   1887758.475 ± 288140.811  ops/s
ZipfianBenchmark.highSkew_writeHeavy_8threads:aborts                  nav  thrpt   15   1841991.000                   #
ZipfianBenchmark.highSkew_writeHeavy_8threads:commits                 nav  thrpt   15  27973124.000                   #
ZipfianBenchmark.lowSkew_readHeavy_1thread                          queue  thrpt   15   3415873.599 ± 196327.765  ops/s
ZipfianBenchmark.lowSkew_readHeavy_1thread:aborts                   queue  thrpt   15           ≈ 0                   #
ZipfianBenchmark.lowSkew_readHeavy_1thread:commits                  queue  thrpt   15  51286938.000                   #
ZipfianBenchmark.lowSkew_readHeavy_1thread                            nav  thrpt   15   1365969.251 ±  72495.450  ops/s
ZipfianBenchmark.lowSkew_readHeavy_1thread:aborts                     nav  thrpt   15           ≈ 0                   #
ZipfianBenchmark.lowSkew_readHeavy_1thread:commits                    nav  thrpt   15  20513068.000                   #
ZipfianBenchmark.lowSkew_readHeavy_2threads                         queue  thrpt   15   3725355.916 ± 230330.986  ops/s
ZipfianBenchmark.lowSkew_readHeavy_2threads:aborts                  queue  thrpt   15      9229.000                   #
ZipfianBenchmark.lowSkew_readHeavy_2threads:commits                 queue  thrpt   15  55938308.000                   #
ZipfianBenchmark.lowSkew_readHeavy_2threads                           nav  thrpt   15   1884607.406 ± 163528.450  ops/s
ZipfianBenchmark.lowSkew_readHeavy_2threads:aborts                    nav  thrpt   15     58699.000                   #
ZipfianBenchmark.lowSkew_readHeavy_2threads:commits                   nav  thrpt   15  28293660.000                   #
ZipfianBenchmark.lowSkew_readHeavy_4threads                         queue  thrpt   15   4671228.113 ± 232495.224  ops/s
ZipfianBenchmark.lowSkew_readHeavy_4threads:aborts                  queue  thrpt   15     34337.000                   #
ZipfianBenchmark.lowSkew_readHeavy_4threads:commits                 queue  thrpt   15  70192449.000                   #
ZipfianBenchmark.lowSkew_readHeavy_4threads                           nav  thrpt   15   2670070.306 ± 217270.577  ops/s
ZipfianBenchmark.lowSkew_readHeavy_4threads:aborts                    nav  thrpt   15     29936.000                   #
ZipfianBenchmark.lowSkew_readHeavy_4threads:commits                   nav  thrpt   15  40201040.000                   #
ZipfianBenchmark.lowSkew_readHeavy_8threads                         queue  thrpt   15   5976820.187 ± 335476.830  ops/s
ZipfianBenchmark.lowSkew_readHeavy_8threads:aborts                  queue  thrpt   15    114780.000                   #
ZipfianBenchmark.lowSkew_readHeavy_8threads:commits                 queue  thrpt   15  91088264.000                   #
ZipfianBenchmark.lowSkew_readHeavy_8threads                           nav  thrpt   15   3510067.800 ± 270407.070  ops/s
ZipfianBenchmark.lowSkew_readHeavy_8threads:aborts                    nav  thrpt   15    109459.000                   #
ZipfianBenchmark.lowSkew_readHeavy_8threads:commits                   nav  thrpt   15  53818747.000                   #
ZipfianBenchmark.lowSkew_writeHeavy_1thread                         queue  thrpt   15   2637877.439 ± 117063.819  ops/s
ZipfianBenchmark.lowSkew_writeHeavy_1thread:aborts                  queue  thrpt   15           ≈ 0                   #
ZipfianBenchmark.lowSkew_writeHeavy_1thread:commits                 queue  thrpt   15  39603389.000                   #
ZipfianBenchmark.lowSkew_writeHeavy_1thread                           nav  thrpt   15    974789.590 ±  58512.151  ops/s
ZipfianBenchmark.lowSkew_writeHeavy_1thread:aborts                    nav  thrpt   15           ≈ 0                   #
ZipfianBenchmark.lowSkew_writeHeavy_1thread:commits                   nav  thrpt   15  14741885.000                   #
ZipfianBenchmark.lowSkew_writeHeavy_2threads                        queue  thrpt   15   3052438.175 ± 229338.826  ops/s
ZipfianBenchmark.lowSkew_writeHeavy_2threads:aborts                 queue  thrpt   15     49874.000                   #
ZipfianBenchmark.lowSkew_writeHeavy_2threads:commits                queue  thrpt   15  45828520.000                   #
ZipfianBenchmark.lowSkew_writeHeavy_2threads                          nav  thrpt   15   1376527.766 ± 119123.444  ops/s
ZipfianBenchmark.lowSkew_writeHeavy_2threads:aborts                   nav  thrpt   15     26142.000                   #
ZipfianBenchmark.lowSkew_writeHeavy_2threads:commits                  nav  thrpt   15  20844385.000                   #
ZipfianBenchmark.lowSkew_writeHeavy_4threads                        queue  thrpt   15   3753860.923 ± 146870.699  ops/s
ZipfianBenchmark.lowSkew_writeHeavy_4threads:aborts                 queue  thrpt   15    218034.000                   #
ZipfianBenchmark.lowSkew_writeHeavy_4threads:commits                queue  thrpt   15  56177050.000                   #
ZipfianBenchmark.lowSkew_writeHeavy_4threads                          nav  thrpt   15   1816686.176 ± 283857.758  ops/s
ZipfianBenchmark.lowSkew_writeHeavy_4threads:aborts                   nav  thrpt   15    153301.000                   #
ZipfianBenchmark.lowSkew_writeHeavy_4threads:commits                  nav  thrpt   15  27613979.000                   #
ZipfianBenchmark.lowSkew_writeHeavy_8threads                        queue  thrpt   15   4052740.379 ± 427334.718  ops/s
ZipfianBenchmark.lowSkew_writeHeavy_8threads:aborts                 queue  thrpt   15    563609.000                   #
ZipfianBenchmark.lowSkew_writeHeavy_8threads:commits                queue  thrpt   15  63042618.000                   #
ZipfianBenchmark.lowSkew_writeHeavy_8threads                          nav  thrpt   15   1868367.977 ± 247523.476  ops/s
ZipfianBenchmark.lowSkew_writeHeavy_8threads:aborts                   nav  thrpt   15    383145.000                   #
ZipfianBenchmark.lowSkew_writeHeavy_8threads:commits                  nav  thrpt   15  28943769.000                   #
```

### With Retries
```java
Benchmark                                              (versionChainType)   Mode  Cnt          Score        Error  Units
ZipfianBenchmark.highSkew_readHeavy_1thread                         queue  thrpt   15    3856804.810 ± 240915.924  ops/s
ZipfianBenchmark.highSkew_readHeavy_1thread:aborts                  queue  thrpt   15            ≈ 0                   #
ZipfianBenchmark.highSkew_readHeavy_1thread:commits                 queue  thrpt   15   57902806.000                   #
ZipfianBenchmark.highSkew_readHeavy_1thread                           nav  thrpt   15    1685010.199 ± 122516.344  ops/s
ZipfianBenchmark.highSkew_readHeavy_1thread:aborts                    nav  thrpt   15            ≈ 0                   #
ZipfianBenchmark.highSkew_readHeavy_1thread:commits                   nav  thrpt   15   25299060.000                   #
ZipfianBenchmark.highSkew_readHeavy_2threads                        queue  thrpt   15    4161446.248 ± 199186.494  ops/s
ZipfianBenchmark.highSkew_readHeavy_2threads:aborts                 queue  thrpt   15     266434.000                   #
ZipfianBenchmark.highSkew_readHeavy_2threads:commits                queue  thrpt   15   62481921.000                   #
ZipfianBenchmark.highSkew_readHeavy_2threads                          nav  thrpt   15    2320202.939 ± 139304.149  ops/s
ZipfianBenchmark.highSkew_readHeavy_2threads:aborts                   nav  thrpt   15     353383.000                   #
ZipfianBenchmark.highSkew_readHeavy_2threads:commits                  nav  thrpt   15   34838362.000                   #
ZipfianBenchmark.highSkew_readHeavy_4threads                        queue  thrpt   15    5480765.121 ± 342161.754  ops/s
ZipfianBenchmark.highSkew_readHeavy_4threads:aborts                 queue  thrpt   15     791971.000                   #
ZipfianBenchmark.highSkew_readHeavy_4threads:commits                queue  thrpt   15   82417188.000                   #
ZipfianBenchmark.highSkew_readHeavy_4threads                          nav  thrpt   15    3401008.228 ± 111429.758  ops/s
ZipfianBenchmark.highSkew_readHeavy_4threads:aborts                   nav  thrpt   15     886945.000                   #
ZipfianBenchmark.highSkew_readHeavy_4threads:commits                  nav  thrpt   15   51379836.000                   #
ZipfianBenchmark.highSkew_readHeavy_8threads                        queue  thrpt   15    6725834.219 ± 559771.297  ops/s
ZipfianBenchmark.highSkew_readHeavy_8threads:aborts                 queue  thrpt   15   18794626.000                   #
ZipfianBenchmark.highSkew_readHeavy_8threads:commits                queue  thrpt   15  104516464.000                   #
ZipfianBenchmark.highSkew_readHeavy_8threads                          nav  thrpt   15    3893709.572 ± 277532.755  ops/s
ZipfianBenchmark.highSkew_readHeavy_8threads:aborts                   nav  thrpt   15   25078075.000                   #
ZipfianBenchmark.highSkew_readHeavy_8threads:commits                  nav  thrpt   15   60408027.000                   #
ZipfianBenchmark.highSkew_writeHeavy_1thread                        queue  thrpt   15    2775937.997 ± 339659.353  ops/s
ZipfianBenchmark.highSkew_writeHeavy_1thread:aborts                 queue  thrpt   15            ≈ 0                   #
ZipfianBenchmark.highSkew_writeHeavy_1thread:commits                queue  thrpt   15   41706518.000                   #
ZipfianBenchmark.highSkew_writeHeavy_1thread                          nav  thrpt   15    1144416.569 ±  96435.194  ops/s
ZipfianBenchmark.highSkew_writeHeavy_1thread:aborts                   nav  thrpt   15            ≈ 0                   #
ZipfianBenchmark.highSkew_writeHeavy_1thread:commits                  nav  thrpt   15   17363496.000                   #
ZipfianBenchmark.highSkew_writeHeavy_2threads                       queue  thrpt   15    3353482.745 ± 161238.812  ops/s
ZipfianBenchmark.highSkew_writeHeavy_2threads:aborts                queue  thrpt   15    1451369.000                   #
ZipfianBenchmark.highSkew_writeHeavy_2threads:commits               queue  thrpt   15   50347871.000                   #
ZipfianBenchmark.highSkew_writeHeavy_2threads                         nav  thrpt   15    1507662.772 ± 141323.427  ops/s
ZipfianBenchmark.highSkew_writeHeavy_2threads:aborts                  nav  thrpt   15    1715857.000                   #
ZipfianBenchmark.highSkew_writeHeavy_2threads:commits                 nav  thrpt   15   22889846.000                   #
ZipfianBenchmark.highSkew_writeHeavy_4threads                       queue  thrpt   15    4086691.229 ± 165008.522  ops/s
ZipfianBenchmark.highSkew_writeHeavy_4threads:aborts                queue  thrpt   15    4315490.000                   #
ZipfianBenchmark.highSkew_writeHeavy_4threads:commits               queue  thrpt   15   61451508.000                   #
ZipfianBenchmark.highSkew_writeHeavy_4threads                         nav  thrpt   15    1923895.692 ± 225025.865  ops/s
ZipfianBenchmark.highSkew_writeHeavy_4threads:aborts                  nav  thrpt   15    5330055.000                   #
ZipfianBenchmark.highSkew_writeHeavy_4threads:commits                 nav  thrpt   15   29234469.000                   #
ZipfianBenchmark.highSkew_writeHeavy_8threads                       queue  thrpt   15    3389623.493 ± 423859.395  ops/s
ZipfianBenchmark.highSkew_writeHeavy_8threads:aborts                queue  thrpt   15   78841900.000                   #
ZipfianBenchmark.highSkew_writeHeavy_8threads:commits               queue  thrpt   15   53705801.000                   #
ZipfianBenchmark.highSkew_writeHeavy_8threads                         nav  thrpt   15    1468193.682 ± 212968.986  ops/s
ZipfianBenchmark.highSkew_writeHeavy_8threads:aborts                  nav  thrpt   15   82563750.000                   #
ZipfianBenchmark.highSkew_writeHeavy_8threads:commits                 nav  thrpt   15   23762636.000                   #
ZipfianBenchmark.lowSkew_readHeavy_1thread                          queue  thrpt   15    3857960.295 ± 290391.189  ops/s
ZipfianBenchmark.lowSkew_readHeavy_1thread:aborts                   queue  thrpt   15            ≈ 0                   #
ZipfianBenchmark.lowSkew_readHeavy_1thread:commits                  queue  thrpt   15   57915121.000                   #
ZipfianBenchmark.lowSkew_readHeavy_1thread                            nav  thrpt   15    1452146.077 ±  74404.871  ops/s
ZipfianBenchmark.lowSkew_readHeavy_1thread:aborts                     nav  thrpt   15            ≈ 0                   #
ZipfianBenchmark.lowSkew_readHeavy_1thread:commits                    nav  thrpt   15   21803364.000                   #
ZipfianBenchmark.lowSkew_readHeavy_2threads                         queue  thrpt   15    4069556.044 ± 275608.489  ops/s
ZipfianBenchmark.lowSkew_readHeavy_2threads:aborts                  queue  thrpt   15      71968.000                   #
ZipfianBenchmark.lowSkew_readHeavy_2threads:commits                 queue  thrpt   15   61109223.000                   #
ZipfianBenchmark.lowSkew_readHeavy_2threads                           nav  thrpt   15    2165688.987 ± 116275.899  ops/s
ZipfianBenchmark.lowSkew_readHeavy_2threads:aborts                    nav  thrpt   15     145487.000                   #
ZipfianBenchmark.lowSkew_readHeavy_2threads:commits                   nav  thrpt   15   32512306.000                   #
ZipfianBenchmark.lowSkew_readHeavy_2threads:jfr                       nav  thrpt                 NaN                 ---
ZipfianBenchmark.lowSkew_readHeavy_4threads                         queue  thrpt   15    5883762.445 ± 311093.561  ops/s
ZipfianBenchmark.lowSkew_readHeavy_4threads:aborts                  queue  thrpt   15     202402.000                   #
ZipfianBenchmark.lowSkew_readHeavy_4threads:commits                 queue  thrpt   15   88374273.000                   #
ZipfianBenchmark.lowSkew_readHeavy_4threads                           nav  thrpt   15    3132439.722 ± 185710.972  ops/s
ZipfianBenchmark.lowSkew_readHeavy_4threads:aborts                    nav  thrpt   15     223849.000                   #
ZipfianBenchmark.lowSkew_readHeavy_4threads:commits                   nav  thrpt   15   47184254.000                   #
ZipfianBenchmark.lowSkew_readHeavy_8threads                         queue  thrpt   15    6928822.170 ± 422590.432  ops/s
ZipfianBenchmark.lowSkew_readHeavy_8threads:aborts                  queue  thrpt   15   21015444.000                   #
ZipfianBenchmark.lowSkew_readHeavy_8threads:commits                 queue  thrpt   15  107363864.000                   #
ZipfianBenchmark.lowSkew_readHeavy_8threads                           nav  thrpt   15    3775515.733 ± 353286.679  ops/s
ZipfianBenchmark.lowSkew_readHeavy_8threads:aborts                    nav  thrpt   15   23888566.000                   #
ZipfianBenchmark.lowSkew_readHeavy_8threads:commits                   nav  thrpt   15   58941352.000                   #
ZipfianBenchmark.lowSkew_writeHeavy_1thread                         queue  thrpt   15    2794729.430 ± 117205.846  ops/s
ZipfianBenchmark.lowSkew_writeHeavy_1thread:aborts                  queue  thrpt   15            ≈ 0                   #
ZipfianBenchmark.lowSkew_writeHeavy_1thread:commits                 queue  thrpt   15   41966662.000                   #
ZipfianBenchmark.lowSkew_writeHeavy_1thread                           nav  thrpt   15    1099048.107 ±  57796.164  ops/s
ZipfianBenchmark.lowSkew_writeHeavy_1thread:aborts                    nav  thrpt   15            ≈ 0                   #
ZipfianBenchmark.lowSkew_writeHeavy_1thread:commits                   nav  thrpt   15   16672964.000                   #
ZipfianBenchmark.lowSkew_writeHeavy_2threads                        queue  thrpt   15    3194669.614 ± 219648.251  ops/s
ZipfianBenchmark.lowSkew_writeHeavy_2threads:aborts                 queue  thrpt   15     368591.000                   #
ZipfianBenchmark.lowSkew_writeHeavy_2threads:commits                queue  thrpt   15   48000128.000                   #
ZipfianBenchmark.lowSkew_writeHeavy_2threads                          nav  thrpt   15    1441714.445 ± 120670.782  ops/s
ZipfianBenchmark.lowSkew_writeHeavy_2threads:aborts                   nav  thrpt   15    1116078.000                   #
ZipfianBenchmark.lowSkew_writeHeavy_2threads:commits                  nav  thrpt   15   22253891.000                   #
ZipfianBenchmark.lowSkew_writeHeavy_4threads                        queue  thrpt   15    4229853.565 ± 142326.206  ops/s
ZipfianBenchmark.lowSkew_writeHeavy_4threads:aborts                 queue  thrpt   15    1079795.000                   #
ZipfianBenchmark.lowSkew_writeHeavy_4threads:commits                queue  thrpt   15   63501269.000                   #
ZipfianBenchmark.lowSkew_writeHeavy_4threads                          nav  thrpt   15    1986430.579 ± 212925.422  ops/s
ZipfianBenchmark.lowSkew_writeHeavy_4threads:aborts                   nav  thrpt   15    1541846.000                   #
ZipfianBenchmark.lowSkew_writeHeavy_4threads:commits                  nav  thrpt   15   30430003.000                   #
ZipfianBenchmark.lowSkew_writeHeavy_8threads                        queue  thrpt   15    3579668.049 ± 485713.193  ops/s
ZipfianBenchmark.lowSkew_writeHeavy_8threads:aborts                 queue  thrpt   15   81328452.000                   #
ZipfianBenchmark.lowSkew_writeHeavy_8threads:commits                queue  thrpt   15   56414191.000                   #
ZipfianBenchmark.lowSkew_writeHeavy_8threads                          nav  thrpt   15    1559722.776 ± 231087.692  ops/s
ZipfianBenchmark.lowSkew_writeHeavy_8threads:aborts                   nav  thrpt   15   75711786.000                   #
ZipfianBenchmark.lowSkew_writeHeavy_8threads:commits                  nav  thrpt   15   24956696.000                   #
```


## Latency with Retries
```java
Benchmark                                              (versionChainType)  Mode  Cnt         Score    Error  Units
ZipfianBenchmark.highSkew_readHeavy_1thread                         queue  avgt   15        ≈ 10⁻⁷            s/op
ZipfianBenchmark.highSkew_readHeavy_1thread:aborts                  queue  avgt   15           ≈ 0               #
ZipfianBenchmark.highSkew_readHeavy_1thread:commits                 queue  avgt   15  51293176.000               #
ZipfianBenchmark.highSkew_readHeavy_1thread                           nav  avgt   15        ≈ 10⁻⁶            s/op
ZipfianBenchmark.highSkew_readHeavy_1thread:aborts                    nav  avgt   15           ≈ 0               #
ZipfianBenchmark.highSkew_readHeavy_1thread:commits                   nav  avgt   15  21915312.000               #
ZipfianBenchmark.highSkew_readHeavy_2threads                        queue  avgt   15        ≈ 10⁻⁶            s/op
ZipfianBenchmark.highSkew_readHeavy_2threads:aborts                 queue  avgt   15    238397.000               #
ZipfianBenchmark.highSkew_readHeavy_2threads:commits                queue  avgt   15  59270869.000               #
ZipfianBenchmark.highSkew_readHeavy_2threads                          nav  avgt   15        ≈ 10⁻⁶            s/op
ZipfianBenchmark.highSkew_readHeavy_2threads:aborts                   nav  avgt   15    221604.000               #
ZipfianBenchmark.highSkew_readHeavy_2threads:commits                  nav  avgt   15  28394927.000               #
ZipfianBenchmark.highSkew_readHeavy_4threads                        queue  avgt   15        ≈ 10⁻⁶            s/op
ZipfianBenchmark.highSkew_readHeavy_4threads:aborts                 queue  avgt   15    612876.000               #
ZipfianBenchmark.highSkew_readHeavy_4threads:commits                queue  avgt   15  65105396.000               #
ZipfianBenchmark.highSkew_readHeavy_4threads                          nav  avgt   15        ≈ 10⁻⁶            s/op
ZipfianBenchmark.highSkew_readHeavy_4threads:aborts                   nav  avgt   15    800321.000               #
ZipfianBenchmark.highSkew_readHeavy_4threads:commits                  nav  avgt   15  38882434.000               #
ZipfianBenchmark.highSkew_readHeavy_8threads                        queue  avgt   15        ≈ 10⁻⁶            s/op
ZipfianBenchmark.highSkew_readHeavy_8threads:aborts                 queue  avgt   15  13822015.000               #
ZipfianBenchmark.highSkew_readHeavy_8threads:commits                queue  avgt   15  86053812.000               #
ZipfianBenchmark.highSkew_readHeavy_8threads                          nav  avgt   15        ≈ 10⁻⁶            s/op
ZipfianBenchmark.highSkew_readHeavy_8threads:aborts                   nav  avgt   15  14333076.000               #
ZipfianBenchmark.highSkew_readHeavy_8threads:commits                  nav  avgt   15  51049286.000               #
ZipfianBenchmark.highSkew_writeHeavy_1thread                        queue  avgt   15        ≈ 10⁻⁶            s/op
ZipfianBenchmark.highSkew_writeHeavy_1thread:aborts                 queue  avgt   15           ≈ 0               #
ZipfianBenchmark.highSkew_writeHeavy_1thread:commits                queue  avgt   15  33952789.000               #
ZipfianBenchmark.highSkew_writeHeavy_1thread                          nav  avgt   15        ≈ 10⁻⁶            s/op
ZipfianBenchmark.highSkew_writeHeavy_1thread:aborts                   nav  avgt   15           ≈ 0               #
ZipfianBenchmark.highSkew_writeHeavy_1thread:commits                  nav  avgt   15  15470961.000               #
ZipfianBenchmark.highSkew_writeHeavy_2threads                       queue  avgt   15        ≈ 10⁻⁶            s/op
ZipfianBenchmark.highSkew_writeHeavy_2threads:aborts                queue  avgt   15   1516746.000               #
ZipfianBenchmark.highSkew_writeHeavy_2threads:commits               queue  avgt   15  54376374.000               #
ZipfianBenchmark.highSkew_writeHeavy_2threads                         nav  avgt   15        ≈ 10⁻⁶            s/op
ZipfianBenchmark.highSkew_writeHeavy_2threads:aborts                  nav  avgt   15   2239167.000               #
ZipfianBenchmark.highSkew_writeHeavy_2threads:commits                 nav  avgt   15  25716794.000               #
ZipfianBenchmark.highSkew_writeHeavy_4threads                       queue  avgt   15        ≈ 10⁻⁶            s/op
ZipfianBenchmark.highSkew_writeHeavy_4threads:aborts                queue  avgt   15   4637852.000               #
ZipfianBenchmark.highSkew_writeHeavy_4threads:commits               queue  avgt   15  67714320.000               #
ZipfianBenchmark.highSkew_writeHeavy_4threads                         nav  avgt   15        ≈ 10⁻⁶            s/op
ZipfianBenchmark.highSkew_writeHeavy_4threads:aborts                  nav  avgt   15   4869113.000               #
ZipfianBenchmark.highSkew_writeHeavy_4threads:commits                 nav  avgt   15  32817422.000               #
ZipfianBenchmark.highSkew_writeHeavy_8threads                       queue  avgt   15        ≈ 10⁻⁶            s/op
ZipfianBenchmark.highSkew_writeHeavy_8threads:aborts                queue  avgt   15  55725011.000               #
ZipfianBenchmark.highSkew_writeHeavy_8threads:commits               queue  avgt   15  60353080.000               #
ZipfianBenchmark.highSkew_writeHeavy_8threads                         nav  avgt   15        ≈ 10⁻⁵            s/op
ZipfianBenchmark.highSkew_writeHeavy_8threads:aborts                  nav  avgt   15  58572056.000               #
ZipfianBenchmark.highSkew_writeHeavy_8threads:commits                 nav  avgt   15  24568501.000               #
ZipfianBenchmark.lowSkew_readHeavy_1thread                          queue  avgt   15        ≈ 10⁻⁷            s/op
ZipfianBenchmark.lowSkew_readHeavy_1thread:aborts                   queue  avgt   15           ≈ 0               #
ZipfianBenchmark.lowSkew_readHeavy_1thread:commits                  queue  avgt   15  58789494.000               #
ZipfianBenchmark.lowSkew_readHeavy_1thread                            nav  avgt   15        ≈ 10⁻⁶            s/op
ZipfianBenchmark.lowSkew_readHeavy_1thread:aborts                     nav  avgt   15           ≈ 0               #
ZipfianBenchmark.lowSkew_readHeavy_1thread:commits                    nav  avgt   15  23405586.000               #
ZipfianBenchmark.lowSkew_readHeavy_2threads                         queue  avgt   15        ≈ 10⁻⁶            s/op
ZipfianBenchmark.lowSkew_readHeavy_2threads:aborts                  queue  avgt   15     37264.000               #
ZipfianBenchmark.lowSkew_readHeavy_2threads:commits                 queue  avgt   15  61383697.000               #
ZipfianBenchmark.lowSkew_readHeavy_2threads                           nav  avgt   15        ≈ 10⁻⁶            s/op
ZipfianBenchmark.lowSkew_readHeavy_2threads:aborts                    nav  avgt   15    103940.000               #
ZipfianBenchmark.lowSkew_readHeavy_2threads:commits                   nav  avgt   15  29887974.000               #
ZipfianBenchmark.lowSkew_readHeavy_4threads                         queue  avgt   15        ≈ 10⁻⁶            s/op
ZipfianBenchmark.lowSkew_readHeavy_4threads:aborts                  queue  avgt   15    123431.000               #
ZipfianBenchmark.lowSkew_readHeavy_4threads:commits                 queue  avgt   15  76621626.000               #
ZipfianBenchmark.lowSkew_readHeavy_4threads                           nav  avgt   15        ≈ 10⁻⁶            s/op
ZipfianBenchmark.lowSkew_readHeavy_4threads:aborts                    nav  avgt   15    200618.000               #
ZipfianBenchmark.lowSkew_readHeavy_4threads:commits                   nav  avgt   15  42221616.000               #
ZipfianBenchmark.lowSkew_readHeavy_8threads                         queue  avgt   15        ≈ 10⁻⁶            s/op
ZipfianBenchmark.lowSkew_readHeavy_8threads:aborts                  queue  avgt   15  10271897.000               #
ZipfianBenchmark.lowSkew_readHeavy_8threads:commits                 queue  avgt   15  96696209.000               #
ZipfianBenchmark.lowSkew_readHeavy_8threads                           nav  avgt   15        ≈ 10⁻⁶            s/op
ZipfianBenchmark.lowSkew_readHeavy_8threads:aborts                    nav  avgt   15   9679794.000               #
ZipfianBenchmark.lowSkew_readHeavy_8threads:commits                   nav  avgt   15  55451863.000               #
ZipfianBenchmark.lowSkew_writeHeavy_1thread                         queue  avgt   15        ≈ 10⁻⁶            s/op
ZipfianBenchmark.lowSkew_writeHeavy_1thread:aborts                  queue  avgt   15           ≈ 0               #
ZipfianBenchmark.lowSkew_writeHeavy_1thread:commits                 queue  avgt   15  41491796.000               #
ZipfianBenchmark.lowSkew_writeHeavy_1thread                           nav  avgt   15        ≈ 10⁻⁶            s/op
ZipfianBenchmark.lowSkew_writeHeavy_1thread:aborts                    nav  avgt   15           ≈ 0               #
ZipfianBenchmark.lowSkew_writeHeavy_1thread:commits                   nav  avgt   15  16468552.000               #
ZipfianBenchmark.lowSkew_writeHeavy_2threads                        queue  avgt   15        ≈ 10⁻⁶            s/op
ZipfianBenchmark.lowSkew_writeHeavy_2threads:aborts                 queue  avgt   15    192319.000               #
ZipfianBenchmark.lowSkew_writeHeavy_2threads:commits                queue  avgt   15  47371678.000               #
ZipfianBenchmark.lowSkew_writeHeavy_2threads                          nav  avgt   15        ≈ 10⁻⁶            s/op
ZipfianBenchmark.lowSkew_writeHeavy_2threads:aborts                   nav  avgt   15    297709.000               #
ZipfianBenchmark.lowSkew_writeHeavy_2threads:commits                  nav  avgt   15  21286937.000               #
ZipfianBenchmark.lowSkew_writeHeavy_4threads                        queue  avgt   15        ≈ 10⁻⁶            s/op
ZipfianBenchmark.lowSkew_writeHeavy_4threads:aborts                 queue  avgt   15    677734.000               #
ZipfianBenchmark.lowSkew_writeHeavy_4threads:commits                queue  avgt   15  58152674.000               #
ZipfianBenchmark.lowSkew_writeHeavy_4threads                          nav  avgt   15        ≈ 10⁻⁶            s/op
ZipfianBenchmark.lowSkew_writeHeavy_4threads:aborts                   nav  avgt   15   1155856.000               #
ZipfianBenchmark.lowSkew_writeHeavy_4threads:commits                  nav  avgt   15  27353470.000               #
ZipfianBenchmark.lowSkew_writeHeavy_8threads                        queue  avgt   15        ≈ 10⁻⁶            s/op
ZipfianBenchmark.lowSkew_writeHeavy_8threads:aborts                 queue  avgt   15  41287344.000               #
ZipfianBenchmark.lowSkew_writeHeavy_8threads:commits                queue  avgt   15  54151286.000               #
ZipfianBenchmark.lowSkew_writeHeavy_8threads                          nav  avgt   15        ≈ 10⁻⁵            s/op
ZipfianBenchmark.lowSkew_writeHeavy_8threads:aborts                   nav  avgt   15  35900700.000               #
ZipfianBenchmark.lowSkew_writeHeavy_8threads:commits                  nav  avgt   15  26167832.000               #
```
# Running the Benchmarks
## Prerequisites
- Java (version 25+)
- Maven

## Steps

1. **Build the jar** from the parent pom:
```bash
   mvn clean package -U
```

2. **Run the benchmarks:**
```bash
   java -jar txmap-benchmarks/target/benchmark.jar {BenchmarkClassName(Without the Parentheses)} -rf json -rff results.json
```

## Output
Results will be saved to `results.json` in your current directory. You can open this with any JMH-compatible visualizer (e.g. [jmh.morethan.io](https://jmh.morethan.io/)).]()