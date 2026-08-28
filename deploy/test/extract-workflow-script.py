"""Extract the remote script from deploy-ec2.yml and make it runnable locally.

The point is to exercise the real text of the script -- wrapper, ERR trap,
git sync, annotation folding -- rather than a paraphrase of it, since every
failure so far has been in the parts that never got tested.
"""
import pathlib
import re
import sys

wf = pathlib.Path("../../.github/workflows/deploy-ec2.yml").resolve()
text = wf.read_text(encoding="utf-8")

lines = text.splitlines()
start = next(i for i, l in enumerate(lines) if l.strip() == "script: |")
indent = len(lines[start]) - len(lines[start].lstrip())
body = []
for line in lines[start + 1:]:
    if line.strip() and (len(line) - len(line.lstrip())) <= indent:
        break
    body.append(line[indent + 2:] if len(line) > indent + 2 else "")
script = "\n".join(body)

app_dir = sys.argv[1]
tag = sys.argv[2]
origin = sys.argv[3]
out = pathlib.Path(sys.argv[4])

script = script.replace("${{ secrets.EC2_APP_DIR }}", app_dir)
script = script.replace("${{ github.event.inputs.tag }}", tag)
script = script.replace("https://github.com/prabhixtechnologies/Mobistack.git", origin)
# The health probe would hit real production from a test; point it at a local
# stub the harness controls instead.
script = script.replace(
    "https://mobistack.prabhixtechnologies.com/actuator/health",
    "http://127.0.0.1:59123/actuator/health",
)

if "${{" in script:
    sys.exit("unsubstituted workflow expression remains: " + re.search(r"\$\{\{[^}]*\}\}", script).group(0))

out.write_text(script, encoding="utf-8", newline="\n")
print(f"extracted {len(body)} lines")
