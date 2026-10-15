import re, json

with open('D:/SourceCode/Ashare/fssc/fssc-web/src/views/GenericList.vue', 'r', encoding='utf-8') as f:
    lines = f.readlines()

# Find CFG block
in_cfg = False
cfg_start = 0
cfg_end = 0
brace_depth = 0
for i, line in enumerate(lines):
    if 'const CFG = {' in line:
        in_cfg = True
        cfg_start = i
        brace_depth = 1
        continue
    if in_cfg:
        brace_depth += line.count('{') - line.count('}')
        if brace_depth <= 0:
            cfg_end = i
            break

cfg_text = ''.join(lines[cfg_start:cfg_end+1])

# Find all route keys like '/system/job-heartbeat': {
route_pattern = r"'(/[^']+)':\s*\{"
routes = re.findall(route_pattern, cfg_text)

# For each route, extract the block and check CRUD
no_crud = []
partial_crud = []
full_crud = []

for i, route in enumerate(routes):
    # Find the block for this route
    start_marker = f"'{route}': {{"
    start_idx = cfg_text.find(start_marker)
    if start_idx == -1:
        continue
    
    # Find matching closing brace
    pos = start_idx + len(start_marker)
    depth = 1
    while depth > 0 and pos < len(cfg_text):
        if cfg_text[pos] == '{':
            depth += 1
        elif cfg_text[pos] == '}':
            depth -= 1
        pos += 1
    
    block = cfg_text[start_idx:pos]
    
    has_create = 'createApi' in block
    has_update = 'updateApi' in block
    has_delete = 'deleteApi' in block
    
    if has_create and has_update and has_delete:
        full_crud.append(route)
    elif not has_create and not has_update and not has_delete:
        no_crud.append(route)
    else:
        missing = []
        if not has_create: missing.append('create')
        if not has_update: missing.append('update')
        if not has_delete: missing.append('delete')
        partial_crud.append((route, missing))

print(f'Total CFG routes: {len(routes)}')
print(f'Full CRUD (C+U+D): {len(full_crud)}')
print(f'No CRUD (read-only): {len(no_crud)}')
print(f'Partial CRUD: {len(partial_crud)}')
print()
print('=== No CRUD (read-only) pages ===')
for r in no_crud:
    print(f'  {r}')
print()
print('=== Partial CRUD pages ===')
for r, m in partial_crud:
    print(f'  {r}: missing {"|".join(m)}')
