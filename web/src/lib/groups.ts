import { useEffect, useState } from "react";
import { api, getFitmentGroup, setFitmentGroup } from "./api";

export interface FitmentGroup {
  id: string;
  name: string;
  callerRole: "OWNER" | "ADMIN" | "MEMBER";
}

export interface FitmentMember {
  kind: "SHOP" | "PERSON";
  subjectId: string;
  label: string;
  role: "OWNER" | "ADMIN" | "MEMBER";
}

export interface FitmentGroupDetail extends FitmentGroup {
  members: FitmentMember[];
  joinCode?: string | null;
}

export function canManageGroup(group: FitmentGroup | null | undefined): boolean {
  return group?.callerRole === "OWNER" || group?.callerRole === "ADMIN";
}

/** Loads the caller's groups and keeps the selected one on the request header. */
export function useFitmentGroups() {
  const [groups, setGroups] = useState<FitmentGroup[]>([]);
  const [selected, setSelected] = useState<string | null>(getFitmentGroup());
  const [ready, setReady] = useState(false);

  useEffect(() => {
    let cancelled = false;
    api<FitmentGroup[]>("/api/v1/groups")
      .then((list) => {
        if (cancelled) {
          return;
        }
        setGroups(list);
        const stored = getFitmentGroup();
        const next = list.some((group) => group.id === stored) ? stored : (list[0]?.id ?? null);
        if (next) {
          setFitmentGroup(next);
          setSelected(next);
        }
      })
      .catch(() => undefined)
      .finally(() => {
        if (!cancelled) {
          setReady(true);
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  function choose(id: string) {
    setFitmentGroup(id);
    setSelected(id);
  }

  async function create(name: string): Promise<void> {
    const created = await api<FitmentGroup>("/api/v1/groups", {
      method: "POST",
      body: JSON.stringify({ name }),
    });
    setFitmentGroup(created.id);
    setSelected(created.id);
    setGroups((current) => [...current.filter((group) => group.id !== created.id), created]);
  }

  const current = groups.find((group) => group.id === selected) ?? null;
  return { groups, selected, choose, create, current, ready };
}
