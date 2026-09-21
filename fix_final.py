with open('app/src/main/java/com/example/MainActivity.kt', 'r') as f:
    lines = f.readlines()

new_lines = []
for i in range(len(lines)):
    line = lines[i]
    if line.strip() == ")" and i + 1 < len(lines) and lines[i+1].strip() == "}":
        if i > 0 and lines[i-1].strip() == "}":
            # This is a `}\n)\n}` sequence. Wait, if it's `}\n)\n}` then I should skip `)\n}`
            pass
            
    # That logic is hard to get exactly right.
