import os

directory = 'app/src/main/java/com/example'

for root, _, files in os.walk(directory):
    for file in files:
        if file.endswith('.kt'):
            filepath = os.path.join(root, file)
            with open(filepath, 'r') as f:
                content = f.read()
            
            if 'collectAsStateWithLifecycle(' in content:
                content = content.replace('.collectAsStateWithLifecycle(', '.collectAsState(')
                content = content.replace('import androidx.lifecycle.compose.collectAsStateWithLifecycle\n', '')
                
                with open(filepath, 'w') as f:
                    f.write(content)
