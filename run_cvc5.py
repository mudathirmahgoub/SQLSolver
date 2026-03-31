import subprocess
import os
import csv
import subprocess
import time

CVC5_BIN = "/home/mudathir/all/cvc5/liastar/build/bin/cvc5"  
dirs = ["cvc5/calcite", "cvc5/spark", "cvc5/tpc-c", "cvc5/tpc-h"]
output_csv = "cvc5_bags.csv"

def run_cvc5(dirs, output_csv):
    with open(output_csv, "w", newline="", encoding="utf-8") as csvfile:
        writer = csv.writer(csvfile)
        writer.writerow(["filename", "result", "duration"])
        for dir in dirs:
            files = sorted(os.listdir(dir))
            for filename in files:
                if filename.endswith(".smt2"):
                    input_path = os.path.join(dir, filename)

                    # Change extension to .txt
                    base = filename[:-5]  # remove ".smt2"
                    output_path = os.path.join(dir, base + ".txt")
                
                    print(f"{input_path}")
                    start = time.time()
                    result = subprocess.run(
                        [CVC5_BIN, input_path, "--tlimit=100000"],
                        capture_output=True,
                        text=True,
                    )
                    end = time.time()
                    content = result.stdout.strip()
                    duration = end - start
                    print(duration)
                    print(content)
                    

                    # Write output to <name>.txt
                    with open(output_path, "w") as out:
                        out.write(result.stdout)
                        out.write(result.stderr)
                    writer.writerow([input_path, content, duration])

run_cvc5(dirs, output_csv)
