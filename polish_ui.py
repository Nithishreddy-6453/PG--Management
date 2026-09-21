import os
import re

def polish_screen(filepath):
    if not os.path.exists(filepath):
        return
    with open(filepath, 'r') as f:
        content = f.read()
    
    # Check if we need to add imports
    if 'import androidx.compose.animation.AnimatedContent' not in content:
        imports_end = content.find('\n\n', content.find('import '))
        if imports_end != -1:
            new_imports = "\nimport androidx.compose.animation.AnimatedContent\nimport androidx.compose.animation.core.tween\nimport androidx.compose.animation.fadeIn\nimport androidx.compose.animation.fadeOut\nimport androidx.compose.animation.togetherWith"
            content = content[:imports_end] + new_imports + content[imports_end:]

    # Replace `when (val state = uiState)` with AnimatedContent
    if 'when (val state = uiState)' in content and 'AnimatedContent' not in content[content.find('when (val state = uiState)') - 50:]:
        pattern = r'(when\s*\(\s*val\s+state\s*=\s*uiState\s*\)\s*\{)(.*?)(^\s*\})'
        # Actually it's easier to just do a simple replacement if the structure matches.
        # But maybe we just add it manually via python or sed.
        
    with open(filepath, 'w') as f:
        f.write(content)

# We can also do a search-replace for AnimatedContent
