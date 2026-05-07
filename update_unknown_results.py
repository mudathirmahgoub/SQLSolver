import csv


def update_csv_from_unknowns(main_file, unknowns_file):
    """Update main CSV file by setting result to UNKNOWN for matching filenames.

    Only the result column is changed; all other values from the main file
    are preserved, including duration.
    """
    unknown_filenames = set()
    with open(unknowns_file, 'r', newline='') as f:
        reader = csv.DictReader(f)
        for row in reader:
            if row['result'] == 'UNKNOWN':
                unknown_filenames.add(row['filename'])

    updated_rows = []
    with open(main_file, 'r', newline='') as f:
        reader = csv.DictReader(f)
        fieldnames = reader.fieldnames
        for row in reader:
            if row['filename'] in unknown_filenames:
                row['result'] = 'UNKNOWN'
            updated_rows.append(row)

    with open(main_file, 'w', newline='') as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(updated_rows)


if __name__ == '__main__':
    files = [
        ('sql_bapa.csv', 'sql_bapa_unknowns.csv'),
        ('sql_mapa.csv', 'sql_mapa_unknowns.csv'),
    ]

    for main_file, unknowns_file in files:
        update_csv_from_unknowns(main_file, unknowns_file)
        print(f'Updated {main_file} using {unknowns_file}')
