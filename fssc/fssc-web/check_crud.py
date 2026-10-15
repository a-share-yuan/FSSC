import re

with open('D:/SourceCode/Ashare/fssc/fssc-web/src/views/GenericList.vue', 'r', encoding='utf-8') as f:
    content = f.read()

# Find all path declarations
path_pattern = r"path:\s*'([^']+)'"
paths = re.findall(path_pattern, content)

results_missing = []
results_no_crud = []
for i, p in enumerate(paths):
    start = content.find("path: '" + p + "'")
    if start == -1:
        start = content.find("path:'" + p + "'")
    next_path = content.find("path: '", start + 10)
    if next_path == -1:
        next_path = len(content)
    block = content[start:next_path]
    
    has_create = 'createApi' in block
    has_update = 'updateApi' in block
    has_delete = 'deleteApi' in block
    
    if not has_create and not has_update and not has_delete:
        results_no_crud.append(p)
    elif not has_create or not has_update or not has_delete:
        missing = []
        if not has_create: missing.append('create')
        if not has_update: missing.append('update')
        if not has_delete: missing.append('delete')
        results_missing.append((p, missing))

print(f'=== Pages with NO CRUD APIs ({len(results_no_crud)}) ===')
for p in results_no_crud:
    print(f'  {p}')
print()
print(f'=== Pages with PARTIAL CRUD ({len(results_missing)}) ===')
for p, m in results_missing:
    print(f'  {p}: missing {"|".join(m)}')
