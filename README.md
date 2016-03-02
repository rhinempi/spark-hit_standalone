# spark-hit_standalone
spark stand alone aligner

Help info:
./bin/spark-hit.sh -h

Build reference index first:
./bin/spark-hit.sh -build reference_genome.fa

Run fragment recruitment:
./bin/spark-hit.sh -fastq raw_data.fq -reference reference_genome.fa -outfile sparkhit.out -global 1 -thread 10
