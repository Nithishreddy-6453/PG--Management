import os

directory = 'app/src/main/java/com/example'

for root, _, files in os.walk(directory):
    for file in files:
        if file.endswith('.kt'):
            filepath = os.path.join(root, file)
            with open(filepath, 'r') as f:
                content = f.read()
            
            if 'collectAsState(' in content or 'collectAsState()' in content:
                content = content.replace('.collectAsState(', '.collectAsStateWithLifecycle(')
                if 'import androidx.lifecycle.compose.collectAsStateWithLifecycle' not in content:
                    # insert import after package or other imports
                    lines = content.split('\n')
                    import_idx = 0
                    for i, line in enumerate(lines):
                        if line.startswith('import '):
                            import_idx = i
                            break
                    if import_idx == 0:
                        for i, line in enumerate(lines):
                            if line.startswith('package '):
                                import_idx = i + 1
                                break
                    lines.insert(import_idx, 'import androidx.lifecycle.compose.collectAsStateWithLifecycle')
                    content = '\n'.join(lines)
                
                with open(filepath, 'w') as f:
                    f.write(content)
