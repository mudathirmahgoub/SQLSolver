def split_file_by_line_number(input_file, odd_file, even_file):
    with open(input_file, "r") as infile, open(odd_file, "w") as odd_out, open(
        even_file, "w"
    ) as even_out:

        for i, line in enumerate(infile, start=1):
            if i % 2 == 1:
                odd_out.write(line)
            else:
                even_out.write(line)


# Example usage:
split_file_by_line_number("sqlsolver_data/calcite/calcite_tests", "q1.sql", "q2.sql")
