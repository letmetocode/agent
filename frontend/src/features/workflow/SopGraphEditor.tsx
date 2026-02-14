import { useEffect, useMemo, useRef, useState } from 'react';
import { Alert, Button, Card, Form, Input, InputNumber, Select, Space, Tag, Typography } from 'antd';
import type { SopSpecDocument, SopSpecGroup, SopSpecStep } from './sopSpecModel';

const { Text } = Typography;

const NODE_WIDTH = 180;
const NODE_HEIGHT = 72;
const CYCLE_PATH_PREVIEW_LIMIT = 6;

const NODE_TYPE_OPTIONS = [
  { label: 'WORKER', value: 'WORKER' },
  { label: 'CRITIC', value: 'CRITIC' }
];

const JOIN_POLICY_OPTIONS = [
  { label: 'all', value: 'all' },
  { label: 'any', value: 'any' },
  { label: 'quorum', value: 'quorum' }
];

const FAILURE_POLICY_OPTIONS = [
  { label: 'failFast', value: 'failFast' },
  { label: 'failSafe', value: 'failSafe' }
];

const normalizePosition = (step: SopSpecStep, index: number) =>
  step.position || {
    x: (index % 4) * 220 + 30,
    y: Math.floor(index / 4) * 130 + 30
  };

const ensurePositionedSteps = (spec: SopSpecDocument): SopSpecDocument => ({
  ...spec,
  steps: spec.steps.map((step, index) => ({
    ...step,
    position: normalizePosition(step, index)
  }))
});

const rebuildGroupsFromSteps = (spec: SopSpecDocument): SopSpecDocument => {
  const existingGroups: SopSpecGroup[] = (spec.groups || []).map((group) => ({
    ...group,
    nodes: [] as string[]
  }));
  const groupMap = new Map<string, SopSpecGroup>();
  existingGroups.forEach((group) => {
    groupMap.set(group.id, group);
  });

  spec.steps.forEach((step) => {
    const groupId = String(step.groupId || '').trim();
    if (!groupId) {
      return;
    }
    let group = groupMap.get(groupId);
    if (!group) {
      group = {
        id: groupId,
        nodes: []
      };
      groupMap.set(groupId, group);
      existingGroups.push(group);
    }
    if (!group.nodes.includes(step.id)) {
      group.nodes.push(step.id);
    }
  });

  return {
    ...spec,
    groups: existingGroups
  };
};

const updateStep = (spec: SopSpecDocument, stepId: string, patch: Partial<SopSpecStep>): SopSpecDocument => ({
  ...spec,
  steps: spec.steps.map((step) =>
    step.id === stepId
      ? {
          ...step,
          ...patch
        }
      : step
  )
});

const updateGroup = (spec: SopSpecDocument, groupId: string, patch: Partial<SopSpecGroup>): SopSpecDocument => ({
  ...spec,
  groups: (spec.groups || []).map((group) =>
    group.id === groupId
      ? {
          ...group,
          ...patch
        }
      : group
  )
});

const ensureUniqueStepId = (steps: SopSpecStep[]) => {
  const used = new Set(steps.map((step) => step.id));
  let index = steps.length + 1;
  let id = `step_${index}`;
  while (used.has(id)) {
    index += 1;
    id = `step_${index}`;
  }
  return id;
};

const detectCyclePaths = (steps: SopSpecStep[]): string[][] => {
  const nodeIds = new Set(steps.map((step) => step.id));
  const depsByNode = new Map(
    steps.map((step) => [
      step.id,
      step.dependsOn.filter((dep) => nodeIds.has(dep))
    ])
  );
  const visiting = new Set<string>();
  const visited = new Set<string>();
  const path: string[] = [];
  const cycles = new Set<string>();
  const cyclePathList: string[][] = [];

  const dfs = (nodeId: string) => {
    if (visiting.has(nodeId)) {
      const start = path.indexOf(nodeId);
      if (start >= 0) {
        const cycle = [...path.slice(start), nodeId];
        const text = cycle.join(' -> ');
        if (!cycles.has(text)) {
          cycles.add(text);
          cyclePathList.push(cycle);
        }
      }
      return;
    }
    if (visited.has(nodeId)) {
      return;
    }
    visiting.add(nodeId);
    path.push(nodeId);
    const deps = depsByNode.get(nodeId) || [];
    deps.forEach((dep) => dfs(dep));
    path.pop();
    visiting.delete(nodeId);
    visited.add(nodeId);
  };

  steps.forEach((step) => dfs(step.id));
  return cyclePathList;
};

interface DragState {
  stepId: string;
  pointerStartX: number;
  pointerStartY: number;
  nodeStartX: number;
  nodeStartY: number;
}

export interface SopGraphEditorProps {
  value: SopSpecDocument;
  onChange: (next: SopSpecDocument) => void;
}

export const SopGraphEditor = ({ value, onChange }: SopGraphEditorProps) => {
  const [selectedNodeId, setSelectedNodeId] = useState<string>();
  const [pendingSource, setPendingSource] = useState<string>();
  const [pendingTarget, setPendingTarget] = useState<string>();
  const [selectedGroupId, setSelectedGroupId] = useState<string>();
  const [batchGroupId, setBatchGroupId] = useState<string>();
  const [batchStepIds, setBatchStepIds] = useState<string[]>([]);
  const [newGroupId, setNewGroupId] = useState('');
  const [selectedCyclePathIndex, setSelectedCyclePathIndex] = useState<number>();
  const [previewFixEdge, setPreviewFixEdge] = useState<{ from: string; to: string; cycleAfter: number }>();
  const [dragState, setDragState] = useState<DragState>();
  const valueRef = useRef(value);
  const canvasRef = useRef<HTMLDivElement | null>(null);

  const positionedSpec = useMemo(() => ensurePositionedSteps(rebuildGroupsFromSteps(value)), [value]);

  useEffect(() => {
    valueRef.current = positionedSpec;
  }, [positionedSpec]);

  useEffect(() => {
    const groups = positionedSpec.groups || [];
    if (!groups.length) {
      setSelectedGroupId(undefined);
      setBatchGroupId(undefined);
      return;
    }
    if (!selectedGroupId || !groups.some((group) => group.id === selectedGroupId)) {
      setSelectedGroupId(groups[0].id);
    }
    if (!batchGroupId || !groups.some((group) => group.id === batchGroupId)) {
      setBatchGroupId(groups[0].id);
    }
  }, [batchGroupId, positionedSpec.groups, selectedGroupId]);

  const nodeMap = useMemo(
    () =>
      new Map(
        positionedSpec.steps.map((step) => [
          step.id,
          {
            step,
            x: step.position?.x || 0,
            y: step.position?.y || 0
          }
        ])
      ),
    [positionedSpec.steps]
  );

  const selectedStep = useMemo(
    () => positionedSpec.steps.find((step) => step.id === selectedNodeId),
    [positionedSpec.steps, selectedNodeId]
  );

  const selectedGroup = useMemo(
    () => (positionedSpec.groups || []).find((group) => group.id === selectedGroupId),
    [positionedSpec.groups, selectedGroupId]
  );

  const cyclePaths = useMemo(() => detectCyclePaths(positionedSpec.steps), [positionedSpec.steps]);
  const cyclePreview = cyclePaths.slice(0, CYCLE_PATH_PREVIEW_LIMIT);
  const selectedCyclePath =
    typeof selectedCyclePathIndex === 'number' && selectedCyclePathIndex >= 0 && selectedCyclePathIndex < cyclePaths.length
      ? cyclePaths[selectedCyclePathIndex]
      : undefined;
  const selectedCycleEdgeKeys = useMemo(() => {
    if (!selectedCyclePath || selectedCyclePath.length < 2) {
      return new Set<string>();
    }
    const keys = new Set<string>();
    for (let i = 0; i < selectedCyclePath.length - 1; i += 1) {
      const from = selectedCyclePath[i + 1];
      const to = selectedCyclePath[i];
      keys.add(`${from}->${to}`);
    }
    return keys;
  }, [selectedCyclePath]);
  const selectedCycleNodeIds = useMemo(
    () => new Set((selectedCyclePath || []).map((item) => item)),
    [selectedCyclePath]
  );
  const previewFixKey = previewFixEdge ? `${previewFixEdge.from}->${previewFixEdge.to}` : '';
  const selectedCycleFixEdges = useMemo(() => {
    if (!selectedCyclePath || selectedCyclePath.length < 2) {
      return [];
    }
    const edges: Array<{ from: string; to: string }> = [];
    for (let i = 0; i < selectedCyclePath.length - 1; i += 1) {
      edges.push({
        from: selectedCyclePath[i + 1],
        to: selectedCyclePath[i]
      });
    }
    return edges.slice(0, 4);
  }, [selectedCyclePath]);

  useEffect(() => {
    if (typeof selectedCyclePathIndex !== 'number') {
      return;
    }
    if (selectedCyclePathIndex < cyclePaths.length) {
      return;
    }
    setSelectedCyclePathIndex(undefined);
  }, [cyclePaths.length, selectedCyclePathIndex]);

  const allStepOptions = positionedSpec.steps.map((step) => ({ label: `${step.name} (${step.id})`, value: step.id }));
  const groupOptions = (positionedSpec.groups || []).map((group) => ({
    label: group.name ? `${group.name} (${group.id})` : group.id,
    value: group.id
  }));

  const applySpecChange = (next: SopSpecDocument) => {
    valueRef.current = next;
    onChange(next);
  };

  const formatCyclePath = (path: string[]) => path.join(' -> ');

  const focusNode = (nodeId: string) => {
    if (!nodeId) {
      return;
    }
    setSelectedNodeId(nodeId);
    const node = nodeMap.get(nodeId);
    const canvas = canvasRef.current;
    if (!node || !canvas) {
      return;
    }
    const centerX = Math.max(0, node.x - canvas.clientWidth / 2 + NODE_WIDTH / 2);
    const centerY = Math.max(0, node.y - canvas.clientHeight / 2 + NODE_HEIGHT / 2);
    canvas.scrollTo({
      left: centerX,
      top: centerY,
      behavior: 'smooth'
    });
  };

  useEffect(() => {
    if (!dragState) {
      return;
    }

    const handleMove = (event: MouseEvent) => {
      const current = valueRef.current;
      const dx = event.clientX - dragState.pointerStartX;
      const dy = event.clientY - dragState.pointerStartY;
      const nextX = Math.max(0, dragState.nodeStartX + dx);
      const nextY = Math.max(0, dragState.nodeStartY + dy);

      const next = updateStep(current, dragState.stepId, {
        position: { x: nextX, y: nextY }
      });
      applySpecChange(next);
    };

    const handleUp = () => {
      setDragState(undefined);
    };

    window.addEventListener('mousemove', handleMove);
    window.addEventListener('mouseup', handleUp);
    return () => {
      window.removeEventListener('mousemove', handleMove);
      window.removeEventListener('mouseup', handleUp);
    };
  }, [dragState]);

  const addNode = () => {
    const nextId = ensureUniqueStepId(positionedSpec.steps);
    const next = rebuildGroupsFromSteps({
      ...positionedSpec,
      steps: [
        ...positionedSpec.steps,
        {
          id: nextId,
          name: `步骤 ${positionedSpec.steps.length + 1}`,
          roleType: 'WORKER',
          dependsOn: [],
          config: {},
          position: {
            x: (positionedSpec.steps.length % 4) * 220 + 30,
            y: Math.floor(positionedSpec.steps.length / 4) * 130 + 30
          }
        }
      ]
    });
    applySpecChange(next);
    setSelectedNodeId(nextId);
  };

  const removeNode = () => {
    if (!selectedNodeId) {
      return;
    }
    const next = rebuildGroupsFromSteps({
      ...positionedSpec,
      steps: positionedSpec.steps
        .filter((step) => step.id !== selectedNodeId)
        .map((step) => ({
          ...step,
          dependsOn: step.dependsOn.filter((dep) => dep !== selectedNodeId)
        }))
    });
    applySpecChange(next);
    setBatchStepIds((previous) => previous.filter((item) => item !== selectedNodeId));
    setSelectedNodeId(undefined);
  };

  const patchSelectedStep = (patch: Partial<SopSpecStep>) => {
    if (!selectedNodeId) {
      return;
    }
    const nextPatch: Partial<SopSpecStep> = { ...patch };
    if ('joinPolicy' in patch && patch.joinPolicy !== 'quorum') {
      nextPatch.quorum = undefined;
    }
    const next = rebuildGroupsFromSteps(updateStep(positionedSpec, selectedNodeId, nextPatch));
    applySpecChange(next);
  };

  const addDependency = () => {
    if (!pendingSource || !pendingTarget || pendingSource === pendingTarget) {
      return;
    }
    const target = positionedSpec.steps.find((step) => step.id === pendingTarget);
    if (!target || target.dependsOn.includes(pendingSource)) {
      return;
    }
    const next = updateStep(positionedSpec, pendingTarget, {
      dependsOn: [...target.dependsOn, pendingSource]
    });
    applySpecChange(next);
  };

  const removeDependency = (source: string, target: string) => {
    const step = positionedSpec.steps.find((item) => item.id === target);
    if (!step) {
      return;
    }
    const next = updateStep(positionedSpec, target, {
      dependsOn: step.dependsOn.filter((item) => item !== source)
    });
    applySpecChange(next);
  };

  const previewRemoveDependency = (source: string, target: string) => {
    const step = positionedSpec.steps.find((item) => item.id === target);
    if (!step || !step.dependsOn.includes(source)) {
      setPreviewFixEdge(undefined);
      return;
    }
    const next = updateStep(positionedSpec, target, {
      dependsOn: step.dependsOn.filter((item) => item !== source)
    });
    const nextCycleCount = detectCyclePaths(next.steps).length;
    setPreviewFixEdge({
      from: source,
      to: target,
      cycleAfter: nextCycleCount
    });
    focusNode(target);
  };

  const addGroup = () => {
    const groupId = newGroupId.trim();
    if (!groupId) {
      return;
    }
    if ((positionedSpec.groups || []).some((group) => group.id === groupId)) {
      setSelectedGroupId(groupId);
      setBatchGroupId(groupId);
      setNewGroupId('');
      return;
    }
    const next: SopSpecDocument = {
      ...positionedSpec,
      groups: [
        ...(positionedSpec.groups || []),
        {
          id: groupId,
          nodes: []
        }
      ]
    };
    applySpecChange(next);
    setSelectedGroupId(groupId);
    setBatchGroupId(groupId);
    setNewGroupId('');
  };

  const removeGroup = (groupId: string) => {
    if (!groupId) {
      return;
    }
    const next = rebuildGroupsFromSteps({
      ...positionedSpec,
      steps: positionedSpec.steps.map((step) =>
        step.groupId === groupId
          ? {
              ...step,
              groupId: undefined
            }
          : step
      ),
      groups: (positionedSpec.groups || []).filter((group) => group.id !== groupId)
    });
    applySpecChange(next);
    if (selectedGroupId === groupId) {
      setSelectedGroupId(undefined);
    }
    if (batchGroupId === groupId) {
      setBatchGroupId(undefined);
    }
  };

  const patchGroupPolicy = (groupId: string, patch: Partial<SopSpecGroup>) => {
    if (!groupId) {
      return;
    }
    const nextPatch: Partial<SopSpecGroup> = { ...patch };
    if ('joinPolicy' in patch && patch.joinPolicy !== 'quorum') {
      nextPatch.quorum = undefined;
    }
    const next = updateGroup(positionedSpec, groupId, nextPatch);
    applySpecChange(next);
  };

  const applyBatchGroup = () => {
    if (!batchGroupId || batchStepIds.length === 0) {
      return;
    }
    const selectedIds = new Set(batchStepIds);
    const next = rebuildGroupsFromSteps({
      ...positionedSpec,
      steps: positionedSpec.steps.map((step) =>
        selectedIds.has(step.id)
          ? {
              ...step,
              groupId: batchGroupId
            }
          : step
      )
    });
    applySpecChange(next);
    setSelectedGroupId(batchGroupId);
  };

  const clearBatchGroup = () => {
    if (batchStepIds.length === 0) {
      return;
    }
    const selectedIds = new Set(batchStepIds);
    const next = rebuildGroupsFromSteps({
      ...positionedSpec,
      steps: positionedSpec.steps.map((step) =>
        selectedIds.has(step.id)
          ? {
              ...step,
              groupId: undefined
            }
          : step
      )
    });
    applySpecChange(next);
  };

  const applyPreviewFix = () => {
    if (!previewFixEdge) {
      return;
    }
    removeDependency(previewFixEdge.from, previewFixEdge.to);
    setSelectedCyclePathIndex(undefined);
    setPreviewFixEdge(undefined);
  };

  const stepQuorumWarning = useMemo(() => {
    if (!selectedStep || selectedStep.joinPolicy !== 'quorum') {
      return '';
    }
    const depCount = selectedStep.dependsOn.length;
    if (depCount <= 0) {
      return '当前节点未配置依赖，无法使用 quorum。';
    }
    if (!selectedStep.quorum || selectedStep.quorum < 1) {
      return '当前节点为 quorum，但 quorum 未设置或非法。';
    }
    if (selectedStep.quorum > depCount) {
      return `quorum(${selectedStep.quorum}) 不能大于依赖数量(${depCount})。`;
    }
    return '';
  }, [selectedStep]);

  const groupQuorumWarning = useMemo(() => {
    if (!selectedGroup || selectedGroup.joinPolicy !== 'quorum') {
      return '';
    }
    const nodeCount = selectedGroup.nodes.length;
    if (nodeCount <= 0) {
      return '当前分组没有成员节点，无法使用 quorum。';
    }
    if (!selectedGroup.quorum || selectedGroup.quorum < 1) {
      return '当前分组为 quorum，但 quorum 未设置或非法。';
    }
    if (selectedGroup.quorum > nodeCount) {
      return `quorum(${selectedGroup.quorum}) 不能大于成员节点数(${nodeCount})。`;
    }
    return '';
  }, [selectedGroup]);

  return (
    <div className="sop-editor-layout">
      <div className="sop-editor-canvas">
        <Space wrap style={{ marginBottom: 8 }}>
          <Button onClick={addNode}>新增节点</Button>
          <Button danger disabled={!selectedNodeId} onClick={removeNode}>
            删除节点
          </Button>
          <Select
            style={{ width: 220 }}
            placeholder="依赖源节点"
            value={pendingSource}
            options={allStepOptions}
            allowClear
            onChange={setPendingSource}
          />
          <Select
            style={{ width: 220 }}
            placeholder="目标节点"
            value={pendingTarget}
            options={allStepOptions}
            allowClear
            onChange={setPendingTarget}
          />
          <Button onClick={addDependency} disabled={!pendingSource || !pendingTarget || pendingSource === pendingTarget}>
            添加依赖连线
          </Button>
          <Text type="secondary">拖拽节点可调整布局。</Text>
        </Space>

        <Space wrap style={{ marginBottom: 8 }}>
          <Select
            mode="multiple"
            style={{ width: 320 }}
            placeholder="批量选择节点"
            value={batchStepIds}
            options={allStepOptions}
            onChange={(values) => setBatchStepIds(values)}
          />
          <Select
            style={{ width: 220 }}
            placeholder="目标分组"
            value={batchGroupId}
            options={groupOptions}
            allowClear
            onChange={(value) => setBatchGroupId(value)}
          />
          <Button onClick={applyBatchGroup} disabled={!batchGroupId || batchStepIds.length === 0}>
            批量设置分组
          </Button>
          <Button onClick={clearBatchGroup} disabled={batchStepIds.length === 0}>
            批量清空分组
          </Button>
          <Input
            style={{ width: 200 }}
            placeholder="新增分组ID"
            value={newGroupId}
            onChange={(event) => setNewGroupId(event.target.value)}
          />
          <Button onClick={addGroup} disabled={!newGroupId.trim()}>
            新增分组
          </Button>
        </Space>

        {cyclePaths.length > 0 ? (
          <Alert
            style={{ marginBottom: 8 }}
            type="warning"
            showIcon
            message={`检测到循环依赖路径：${cyclePaths.length} 条`}
            description={
              <Space direction="vertical" size={4}>
                {cyclePreview.map((item, index) => (
                  <Space key={formatCyclePath(item)} wrap>
                    <Text code>{formatCyclePath(item)}</Text>
                    <Button
                      size="small"
                      type={selectedCyclePathIndex === index ? 'primary' : 'default'}
                      onClick={() => {
                        setSelectedCyclePathIndex(index);
                        if (item.length > 0) {
                          focusNode(item[0]);
                        }
                      }}
                    >
                      定位路径
                    </Button>
                  </Space>
                ))}
                {cyclePaths.length > cyclePreview.length ? (
                  <Text type="secondary">其余 {cyclePaths.length - cyclePreview.length} 条路径请在依赖关系中继续排查。</Text>
                ) : null}
                {selectedCycleFixEdges.length > 0 ? (
                  <Space direction="vertical" size={2}>
                    <Text type="secondary">可修复建议（选择一条回边移除）：</Text>
                    <Space wrap>
                      {selectedCycleFixEdges.map((edge) => (
                        <Space key={`${edge.from}->${edge.to}`} size={4}>
                          <Button
                            size="small"
                            type={previewFixKey === `${edge.from}->${edge.to}` ? 'primary' : 'default'}
                            onClick={() => previewRemoveDependency(edge.from, edge.to)}
                          >
                            预演 {edge.from} -&gt; {edge.to}
                          </Button>
                          <Button
                            size="small"
                            onClick={() => {
                              removeDependency(edge.from, edge.to);
                              setSelectedCyclePathIndex(undefined);
                              setPreviewFixEdge(undefined);
                              focusNode(edge.to);
                            }}
                          >
                            直接移除
                          </Button>
                        </Space>
                      ))}
                    </Space>
                    {previewFixEdge ? (
                      <Alert
                        type="info"
                        showIcon
                        message={`预演结果：移除 ${previewFixEdge.from} -> ${previewFixEdge.to} 后，循环路径 ${cyclePaths.length} -> ${previewFixEdge.cycleAfter}`}
                        action={
                          <Space>
                            <Button size="small" type="primary" onClick={applyPreviewFix}>
                              应用预演修复
                            </Button>
                            <Button size="small" onClick={() => setPreviewFixEdge(undefined)}>
                              取消预演
                            </Button>
                          </Space>
                        }
                      />
                    ) : null}
                    <Text type="secondary">若业务必须保留依赖，建议将环中节点拆分为“前置准备 + 执行”两步再重连。</Text>
                  </Space>
                ) : null}
                {typeof selectedCyclePathIndex === 'number' ? (
                  <Button
                    size="small"
                    onClick={() => {
                      setSelectedCyclePathIndex(undefined);
                      setPreviewFixEdge(undefined);
                    }}
                  >
                    清除高亮
                  </Button>
                ) : null}
              </Space>
            }
          />
        ) : null}

        <div ref={canvasRef} className="sop-flow-wrapper">
          <svg className="sop-edge-layer" width="100%" height="100%">
            <defs>
              <marker id="sop-arrow" markerWidth="8" markerHeight="8" refX="6" refY="3" orient="auto" markerUnits="strokeWidth">
                <path d="M0,0 L0,6 L7,3 z" fill="#8c8c8c" />
              </marker>
            </defs>
            {positionedSpec.steps.flatMap((target) =>
              target.dependsOn.map((sourceId) => {
                const source = nodeMap.get(sourceId);
                const to = nodeMap.get(target.id);
                if (!source || !to) {
                  return null;
                }
                const x1 = source.x + NODE_WIDTH;
                const y1 = source.y + NODE_HEIGHT / 2;
                const x2 = to.x;
                const y2 = to.y + NODE_HEIGHT / 2;
                const key = `${sourceId}->${target.id}`;
                const selected = selectedNodeId && (selectedNodeId === sourceId || selectedNodeId === target.id);
                const inSelectedCycle = selectedCycleEdgeKeys.has(key);
                const inPreviewFix = previewFixKey === key;
                return (
                  <g key={key}>
                    <path
                      d={`M ${x1} ${y1} C ${x1 + 40} ${y1}, ${x2 - 40} ${y2}, ${x2} ${y2}`}
                      stroke={inPreviewFix ? '#fa8c16' : inSelectedCycle ? '#ff4d4f' : selected ? '#1677ff' : '#8c8c8c'}
                      strokeWidth={inPreviewFix ? 2.4 : inSelectedCycle ? 2.2 : selected ? 1.6 : 1.1}
                      fill="none"
                      markerEnd="url(#sop-arrow)"
                    />
                    <title>{`${sourceId} -> ${target.id}`}</title>
                  </g>
                );
              })
            )}
          </svg>

          {positionedSpec.steps.map((step, index) => {
            const position = normalizePosition(step, index);
            const isSelected = selectedNodeId === step.id;
            return (
              <div
                key={step.id}
                className={`sop-node-card ${isSelected ? 'selected' : ''}`}
                style={{
                  left: position.x,
                  top: position.y,
                  borderColor: isSelected ? '#1677ff' : selectedCycleNodeIds.has(step.id) ? '#ff4d4f' : '#d9d9d9',
                  background: step.roleType === 'CRITIC' ? '#fff7e6' : '#f6ffed'
                }}
                onMouseDown={(event) => {
                  if (event.button !== 0) {
                    return;
                  }
                  setSelectedNodeId(step.id);
                  setDragState({
                    stepId: step.id,
                    pointerStartX: event.clientX,
                    pointerStartY: event.clientY,
                    nodeStartX: position.x,
                    nodeStartY: position.y
                  });
                }}
              >
                <div className="sop-node-title">{step.name || step.id}</div>
                <div className="sop-node-subtitle">
                  <Space size={4}>
                    <Tag color={step.roleType === 'CRITIC' ? 'orange' : 'green'}>{step.roleType}</Tag>
                    {step.groupId ? <Tag color="blue">{step.groupId}</Tag> : null}
                  </Space>
                  <Text type="secondary" style={{ fontSize: 12 }}>
                    {step.id}
                  </Text>
                </div>
              </div>
            );
          })}
        </div>

        {selectedStep ? (
          <Card size="small" style={{ marginTop: 12 }} title={`节点依赖：${selectedStep.id}`}>
            {selectedStep.dependsOn.length === 0 ? (
              <Text type="secondary">暂无依赖</Text>
            ) : (
              <Space wrap>
                {selectedStep.dependsOn.map((dep) => (
                  <Tag key={dep} closable onClose={() => removeDependency(dep, selectedStep.id)}>
                    {dep}
                  </Tag>
                ))}
              </Space>
            )}
          </Card>
        ) : null}
      </div>

      <Space direction="vertical" size={12} style={{ width: '100%' }}>
        <Card size="small" title="节点属性" className="sop-editor-panel">
          {!selectedStep ? (
            <Alert type="info" showIcon message="请选择一个节点后编辑属性" />
          ) : (
            <Form layout="vertical" size="small">
              <Form.Item label="节点ID">
                <Input value={selectedStep.id} disabled />
              </Form.Item>
              <Form.Item label="节点名称">
                <Input
                  value={selectedStep.name}
                  onChange={(event) => patchSelectedStep({ name: event.target.value })}
                  placeholder="请输入节点名称"
                />
              </Form.Item>
              <Form.Item label="节点类型">
                <Select
                  value={selectedStep.roleType}
                  options={NODE_TYPE_OPTIONS}
                  onChange={(value) => patchSelectedStep({ roleType: value })}
                />
              </Form.Item>
              <Form.Item label="分组ID（可选）">
                <Select
                  allowClear
                  showSearch
                  value={selectedStep.groupId}
                  options={groupOptions}
                  placeholder="选择已有分组（可在左侧新增）"
                  onChange={(value) => patchSelectedStep({ groupId: value || undefined })}
                />
              </Form.Item>
              <Form.Item label="joinPolicy（可选）">
                <Select
                  allowClear
                  value={selectedStep.joinPolicy}
                  options={JOIN_POLICY_OPTIONS}
                  onChange={(value) =>
                    patchSelectedStep({
                      joinPolicy: value || undefined,
                      quorum: value === 'quorum' ? selectedStep.quorum || 1 : undefined
                    })
                  }
                />
              </Form.Item>
              <Form.Item label="failurePolicy（可选）">
                <Select
                  allowClear
                  value={selectedStep.failurePolicy}
                  options={FAILURE_POLICY_OPTIONS}
                  onChange={(value) => patchSelectedStep({ failurePolicy: value || undefined })}
                />
              </Form.Item>
              <Form.Item label="quorum（可选）">
                <InputNumber
                  style={{ width: '100%' }}
                  min={1}
                  max={selectedStep.joinPolicy === 'quorum' ? Math.max(selectedStep.dependsOn.length, 1) : undefined}
                  disabled={selectedStep.joinPolicy !== 'quorum'}
                  value={selectedStep.quorum}
                  onChange={(value) => patchSelectedStep({ quorum: typeof value === 'number' ? value : undefined })}
                />
              </Form.Item>
              {stepQuorumWarning ? <Alert type="warning" showIcon message={stepQuorumWarning} /> : null}
            </Form>
          )}
        </Card>

        <Card size="small" title="分组策略" className="sop-editor-panel">
          {!selectedGroup ? (
            <Alert type="info" showIcon message="暂无分组，可在左侧输入分组ID后新增" />
          ) : (
            <Form layout="vertical" size="small">
              <Form.Item label="选择分组">
                <Space.Compact style={{ width: '100%' }}>
                  <Select style={{ width: '100%' }} value={selectedGroup.id} options={groupOptions} onChange={setSelectedGroupId} />
                  <Button danger onClick={() => removeGroup(selectedGroup.id)}>
                    删除分组
                  </Button>
                </Space.Compact>
              </Form.Item>
              <Form.Item label="分组名称（可选）">
                <Input
                  value={selectedGroup.name}
                  onChange={(event) => patchGroupPolicy(selectedGroup.id, { name: event.target.value || undefined })}
                  placeholder="例如：research-group"
                />
              </Form.Item>
              <Form.Item label="joinPolicy（可选）">
                <Select
                  allowClear
                  value={selectedGroup.joinPolicy}
                  options={JOIN_POLICY_OPTIONS}
                  onChange={(value) =>
                    patchGroupPolicy(selectedGroup.id, {
                      joinPolicy: value || undefined,
                      quorum: value === 'quorum' ? selectedGroup.quorum || 1 : undefined
                    })
                  }
                />
              </Form.Item>
              <Form.Item label="failurePolicy（可选）">
                <Select
                  allowClear
                  value={selectedGroup.failurePolicy}
                  options={FAILURE_POLICY_OPTIONS}
                  onChange={(value) => patchGroupPolicy(selectedGroup.id, { failurePolicy: value || undefined })}
                />
              </Form.Item>
              <Form.Item label="quorum（可选）">
                <InputNumber
                  style={{ width: '100%' }}
                  min={1}
                  max={selectedGroup.joinPolicy === 'quorum' ? Math.max(selectedGroup.nodes.length, 1) : undefined}
                  disabled={selectedGroup.joinPolicy !== 'quorum'}
                  value={selectedGroup.quorum}
                  onChange={(value) => patchGroupPolicy(selectedGroup.id, { quorum: typeof value === 'number' ? value : undefined })}
                />
              </Form.Item>
              <Form.Item label="runPolicy（可选）">
                <Input
                  value={selectedGroup.runPolicy}
                  onChange={(event) => patchGroupPolicy(selectedGroup.id, { runPolicy: event.target.value || undefined })}
                  placeholder="例如：parallel"
                />
              </Form.Item>
              <Form.Item label="成员节点">
                {selectedGroup.nodes.length === 0 ? (
                  <Text type="secondary">暂无成员，可使用“批量设置分组”添加。</Text>
                ) : (
                  <Space wrap>
                    {selectedGroup.nodes.map((nodeId) => (
                      <Tag key={nodeId}>{nodeId}</Tag>
                    ))}
                  </Space>
                )}
              </Form.Item>
              {groupQuorumWarning ? <Alert type="warning" showIcon message={groupQuorumWarning} /> : null}
            </Form>
          )}
        </Card>
      </Space>
    </div>
  );
};
