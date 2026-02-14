export type SopRoleType = 'WORKER' | 'CRITIC';

export interface SopSpecStep {
  id: string;
  name: string;
  roleType: SopRoleType;
  dependsOn: string[];
  config: Record<string, unknown>;
  groupId?: string;
  joinPolicy?: 'all' | 'any' | 'quorum';
  failurePolicy?: 'failFast' | 'failSafe';
  quorum?: number;
  runPolicy?: string;
  position?: { x: number; y: number };
}

export interface SopSpecGroup {
  id: string;
  name?: string;
  nodes: string[];
  joinPolicy?: 'all' | 'any' | 'quorum';
  failurePolicy?: 'failFast' | 'failSafe';
  quorum?: number;
  runPolicy?: string;
}

export interface SopSpecDocument {
  meta?: Record<string, unknown>;
  params?: Record<string, unknown>;
  steps: SopSpecStep[];
  groups?: SopSpecGroup[];
  fallbackPolicy?: {
    mode: 'singleNodeGuaranteed';
  };
}

const DEFAULT_POSITION_GAP_X = 240;
const DEFAULT_POSITION_GAP_Y = 120;

const normalizeRoleType = (value: unknown): SopRoleType => {
  if (String(value || '').toUpperCase() === 'CRITIC') {
    return 'CRITIC';
  }
  return 'WORKER';
};

const toStringArray = (value: unknown): string[] => {
  if (!Array.isArray(value)) {
    return [];
  }
  const dedup = new Set<string>();
  value.forEach((item) => {
    const text = String(item || '').trim();
    if (text) {
      dedup.add(text);
    }
  });
  return Array.from(dedup);
};

const toNumber = (value: unknown): number | undefined => {
  if (value === undefined || value === null || value === '') {
    return undefined;
  }
  const num = Number(value);
  return Number.isFinite(num) ? num : undefined;
};

const toRecord = (value: unknown): Record<string, unknown> => {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    return {};
  }
  return { ...(value as Record<string, unknown>) };
};

const normalizePosition = (value: unknown, index: number): { x: number; y: number } => {
  if (value && typeof value === 'object' && !Array.isArray(value)) {
    const x = Number((value as Record<string, unknown>).x);
    const y = Number((value as Record<string, unknown>).y);
    if (Number.isFinite(x) && Number.isFinite(y)) {
      return { x, y };
    }
  }
  return {
    x: (index % 4) * DEFAULT_POSITION_GAP_X,
    y: Math.floor(index / 4) * DEFAULT_POSITION_GAP_Y
  };
};

export const graphToSopSpec = (
  graphDefinition?: Record<string, unknown>,
  draftName?: string
): SopSpecDocument => {
  const graph = toRecord(graphDefinition);
  const nodes = Array.isArray(graph.nodes) ? graph.nodes : [];
  const edges = Array.isArray(graph.edges) ? graph.edges : [];
  const groups = Array.isArray(graph.groups) ? graph.groups : [];

  const depsByNode = new Map<string, string[]>();
  edges.forEach((edge) => {
    if (!edge || typeof edge !== 'object') {
      return;
    }
    const edgeMap = edge as Record<string, unknown>;
    const from = String(edgeMap.from || '').trim();
    const to = String(edgeMap.to || '').trim();
    if (!from || !to) {
      return;
    }
    const deps = depsByNode.get(to) || [];
    if (!deps.includes(from)) {
      deps.push(from);
    }
    depsByNode.set(to, deps);
  });

  const steps: SopSpecStep[] = [];
  nodes.forEach((node, index) => {
    if (!node || typeof node !== 'object') {
      return;
    }
    const nodeMap = node as Record<string, unknown>;
    const id = String(nodeMap.id || '').trim();
    if (!id) {
      return;
    }
    const step: SopSpecStep = {
      id,
      name: String(nodeMap.name || id),
      roleType: normalizeRoleType(nodeMap.type),
      dependsOn: depsByNode.get(id) || [],
      config: toRecord(nodeMap.config),
      position: normalizePosition(nodeMap.position, index)
    };
    const groupId = String(nodeMap.groupId || '').trim();
    if (groupId) {
      step.groupId = groupId;
    }
    const joinPolicy = nodeMap.joinPolicy as SopSpecStep['joinPolicy'];
    if (joinPolicy) {
      step.joinPolicy = joinPolicy;
    }
    const failurePolicy = nodeMap.failurePolicy as SopSpecStep['failurePolicy'];
    if (failurePolicy) {
      step.failurePolicy = failurePolicy;
    }
    const quorum = toNumber(nodeMap.quorum);
    if (typeof quorum === 'number') {
      step.quorum = quorum;
    }
    if (nodeMap.runPolicy) {
      step.runPolicy = String(nodeMap.runPolicy);
    }
    steps.push(step);
  });

  const normalizedGroups: SopSpecGroup[] = [];
  groups.forEach((group) => {
    if (!group || typeof group !== 'object') {
      return;
    }
    const groupMap = group as Record<string, unknown>;
    const id = String(groupMap.id || '').trim();
    if (!id) {
      return;
    }
    const normalized: SopSpecGroup = {
      id,
      nodes: toStringArray(groupMap.nodes)
    };
    if (groupMap.name) {
      normalized.name = String(groupMap.name);
    }
    const joinPolicy = groupMap.joinPolicy as SopSpecGroup['joinPolicy'];
    if (joinPolicy) {
      normalized.joinPolicy = joinPolicy;
    }
    const failurePolicy = groupMap.failurePolicy as SopSpecGroup['failurePolicy'];
    if (failurePolicy) {
      normalized.failurePolicy = failurePolicy;
    }
    const quorum = toNumber(groupMap.quorum);
    if (typeof quorum === 'number') {
      normalized.quorum = quorum;
    }
    if (groupMap.runPolicy) {
      normalized.runPolicy = String(groupMap.runPolicy);
    }
    normalizedGroups.push(normalized);
  });

  return {
    meta: { name: draftName || 'SOP 模板' },
    steps,
    groups: normalizedGroups,
    fallbackPolicy: { mode: 'singleNodeGuaranteed' }
  };
};

export const normalizeSopSpec = (raw?: Record<string, unknown> | null): SopSpecDocument => {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) {
    return {
      meta: { name: 'SOP 模板' },
      steps: [],
      groups: [],
      fallbackPolicy: { mode: 'singleNodeGuaranteed' }
    };
  }
  const map = raw as Record<string, unknown>;
  const stepsRaw = Array.isArray(map.steps) ? map.steps : [];
  const groupsRaw = Array.isArray(map.groups) ? map.groups : [];

  const steps: SopSpecStep[] = [];
  stepsRaw.forEach((item, index) => {
    if (!item || typeof item !== 'object') {
      return;
    }
    const step = item as Record<string, unknown>;
    const id = String(step.id || '').trim();
    if (!id) {
      return;
    }
    const normalized: SopSpecStep = {
      id,
      name: String(step.name || id),
      roleType: normalizeRoleType(step.roleType || step.type),
      dependsOn: toStringArray(step.dependsOn),
      config: toRecord(step.config),
      position: normalizePosition(step.position, index)
    };
    const groupId = String(step.groupId || '').trim();
    if (groupId) {
      normalized.groupId = groupId;
    }
    const joinPolicy = step.joinPolicy as SopSpecStep['joinPolicy'];
    if (joinPolicy) {
      normalized.joinPolicy = joinPolicy;
    }
    const failurePolicy = step.failurePolicy as SopSpecStep['failurePolicy'];
    if (failurePolicy) {
      normalized.failurePolicy = failurePolicy;
    }
    const quorum = toNumber(step.quorum);
    if (typeof quorum === 'number') {
      normalized.quorum = quorum;
    }
    if (step.runPolicy) {
      normalized.runPolicy = String(step.runPolicy);
    }
    steps.push(normalized);
  });

  const groups: SopSpecGroup[] = [];
  groupsRaw.forEach((item) => {
    if (!item || typeof item !== 'object') {
      return;
    }
    const group = item as Record<string, unknown>;
    const id = String(group.id || '').trim();
    if (!id) {
      return;
    }
    const normalized: SopSpecGroup = {
      id,
      nodes: toStringArray(group.nodes)
    };
    if (group.name) {
      normalized.name = String(group.name);
    }
    const joinPolicy = group.joinPolicy as SopSpecGroup['joinPolicy'];
    if (joinPolicy) {
      normalized.joinPolicy = joinPolicy;
    }
    const failurePolicy = group.failurePolicy as SopSpecGroup['failurePolicy'];
    if (failurePolicy) {
      normalized.failurePolicy = failurePolicy;
    }
    const quorum = toNumber(group.quorum);
    if (typeof quorum === 'number') {
      normalized.quorum = quorum;
    }
    if (group.runPolicy) {
      normalized.runPolicy = String(group.runPolicy);
    }
    groups.push(normalized);
  });

  return {
    meta: toRecord(map.meta),
    params: toRecord(map.params),
    steps,
    groups,
    fallbackPolicy: { mode: 'singleNodeGuaranteed' }
  };
};

export const sopSpecToPayload = (spec: SopSpecDocument): Record<string, unknown> => ({
  meta: spec.meta || {},
  params: spec.params || {},
  fallbackPolicy: spec.fallbackPolicy || { mode: 'singleNodeGuaranteed' },
  groups: (spec.groups || []).map((group) => ({
    id: group.id,
    name: group.name,
    nodes: group.nodes,
    joinPolicy: group.joinPolicy,
    failurePolicy: group.failurePolicy,
    quorum: group.quorum,
    runPolicy: group.runPolicy
  })),
  steps: spec.steps.map((step) => ({
    id: step.id,
    name: step.name,
    roleType: step.roleType,
    dependsOn: step.dependsOn,
    config: step.config || {},
    groupId: step.groupId,
    joinPolicy: step.joinPolicy,
    failurePolicy: step.failurePolicy,
    quorum: step.quorum,
    runPolicy: step.runPolicy,
    position: step.position
  }))
});
