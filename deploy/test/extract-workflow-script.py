"""Pull a shell block out of a workflow file so a test can run the real thing.

Every deploy failure so far has been in text that only ever executed on the
server or on a runner, where a mistake costs a release. These blocks are
extracted rather than copied so a test cannot quietly drift from what ships.

  --step  the step's `- name:` value, since a workflow has several shell blocks
          and testing the wrong one is worse than testing nothing
  --key   `script` for an ssh-action step, `run` for a normal one
"""
import argparse
import pathlib
import re
import sys

WORKFLOWS = pathlib.Path(__file__).resolve().parent.parent.parent / ".github" / "workflows"


def block(workflow: str, step: str, key: str) -> str:
    path = WORKFLOWS / workflow
    lines = path.read_text(encoding="utf-8").splitlines()
    try:
        at = next(i for i, l in enumerate(lines) if l.strip() == f"- name: {step}")
    except StopIteration:
        sys.exit(f"no step named {step!r} in {path}")
    try:
        start = next(i for i, l in enumerate(lines[at:], at) if l.strip() == f"{key}: |")
    except StopIteration:
        sys.exit(f"step {step!r} in {path} has no `{key}: |` block")

    indent = len(lines[start]) - len(lines[start].lstrip())
    body = []
    for line in lines[start + 1:]:
        # The block ends at the first non-blank line indented no further than
        # the key itself, which is the next step or the next YAML mapping.
        if line.strip() and (len(line) - len(line.lstrip())) <= indent:
            break
        body.append(line[indent + 2:] if len(line) > indent + 2 else "")
    return "\n".join(body)


parser = argparse.ArgumentParser()
parser.add_argument("--workflow", default="deploy-ec2.yml")
parser.add_argument("--step", required=True)
parser.add_argument("--key", default="script")
parser.add_argument("--out", required=True)
parser.add_argument("--app-dir")
parser.add_argument("--tag")
parser.add_argument("--origin")
args = parser.parse_args()

script = block(args.workflow, args.step, args.key)

if args.app_dir:
    script = script.replace("${{ secrets.EC2_APP_DIR }}", args.app_dir)
if args.tag:
    script = script.replace("${{ needs.resolve.outputs.sha }}", args.tag)
if args.origin:
    script = script.replace("https://github.com/prabhixtechnologies/Mobistack.git", args.origin)
# The health probe would hit real production from a test; point it at a local
# port nothing is listening on so the stub curl decides the outcome.
script = script.replace(
    "https://mobistack.prabhixtechnologies.com/actuator/health",
    "http://127.0.0.1:59123/actuator/health",
)

left = re.search(r"\$\{\{[^}]*\}\}", script)
if left:
    sys.exit(f"unsubstituted workflow expression remains: {left.group(0)}")

pathlib.Path(args.out).write_text(script, encoding="utf-8", newline="\n")
print(f"extracted {len(script.splitlines())} lines of {args.key} from {args.step!r}")
