package com.ruoyi.station.console.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.station.console.domain.SiteFolder;
import com.ruoyi.station.console.domain.SiteInfo;
import com.ruoyi.station.console.mapper.SiteFolderMapper;
import com.ruoyi.station.console.mapper.SiteImageMapper;
import com.ruoyi.station.console.mapper.SiteVideoMapper;
import com.ruoyi.station.console.mapper.SiteModelMapper;

/**
 * 文件资源的目录树。
 *
 * <p>图片和视频各一棵，靠 mediaType（image / video）分开。每个公开方法都要求传它，
 * 没有「不分类型」的重载——那样很容易在某条路径上漏掉，而漏掉的后果是两棵树串在一起，
 * 页面上看着像数据错乱。</p>
 *
 * <h3>⚠ 删文件夹不删文件</h3>
 *
 * <p>删除时里面的文件 folderid 置回 null（回落到根），文件本身一个不动。图片很可能
 * 正被商品页或博客引用着，跟着文件夹一起删掉，前台就是一片裂图，而且删了找不回来。
 * 子文件夹同理往上提一级，不做级联删除。</p>
 */
@Service
public class SiteFolderService
{
    /** 三棵树的类型标识。⚠ 和前端传上来的字符串、以及库里 mediatype 列的值必须一致 */
    public static final String TYPE_IMAGE = "image";
    public static final String TYPE_VIDEO = "video";
    /** 3D 模型（.glb），2026-09-20 加 */
    public static final String TYPE_MODEL = "model";

    /**
     * 嵌套深度上限。
     *
     * <p>不是技术限制，是防手滑：树整棵读进内存再递归拼，太深会让前端的 el-tree
     * 缩进到没法看。真需要更深的目录结构，多半是分类方式本身有问题。</p>
     */
    private static final int MAX_DEPTH = 10;

    @Autowired
    private SiteFolderMapper folderMapper;

    @Autowired
    private SiteImageMapper imageMapper;

    @Autowired
    private SiteVideoMapper videoMapper;

    @Autowired
    private SiteModelMapper modelMapper;

    @Autowired
    private SiteInfoService siteInfoService;

    @Autowired
    private CodeGenerator codeGenerator;

    /**
     * 整棵树，顶层在最外面，每个节点的 children 已经拼好。
     *
     * <p>一次查全部行 + 一次查文件数，在内存里拼——文件夹总共几十个，递归查库反而是
     * N+1 次往返。</p>
     */
    public List<SiteFolder> tree(String mediaType)
    {
        String type = normalizeType(mediaType);
        List<SiteFolder> all = folderMapper.selectByType(type);

        // 文件数一次查完再合并。⚠ countFiles 返回的是投影，只有 folderId 和 fileCount
        Map<String, Integer> counts = new HashMap<>();
        for (SiteFolder row : folderMapper.countFiles(type))
        {
            counts.put(row.getFolderId(), row.getFileCount() == null ? 0 : row.getFileCount());
        }

        Map<String, SiteFolder> byId = new HashMap<>();
        for (SiteFolder folder : all)
        {
            folder.setFileCount(counts.getOrDefault(folder.getFolderId(), 0));
            folder.setChildren(new ArrayList<>());
            byId.put(folder.getFolderId(), folder);
        }

        List<SiteFolder> roots = new ArrayList<>();
        for (SiteFolder folder : all)
        {
            String parentId = folder.getParentId();
            SiteFolder parent = parentId == null ? null : byId.get(parentId);
            if (parent == null)
            {
                // 顶层，或者父行不知怎么没了（不该发生）——那也当顶层。
                // 总比让这个文件夹从树上消失、里面的文件再也点不到强
                roots.add(folder);
            }
            else
            {
                parent.getChildren().add(folder);
            }
        }
        return roots;
    }

    @Transactional(rollbackFor = Exception.class)
    public SiteFolder add(String mediaType, String parentId, String folderName, String operator)
    {
        String type = normalizeType(mediaType);
        String name = requireName(folderName);
        String parent = blankToNull(parentId);

        if (parent != null)
        {
            SiteFolder parentFolder = folderMapper.selectById(parent);
            if (parentFolder == null || !type.equals(parentFolder.getMediaType()))
            {
                // 类型对不上说明前端把另一棵树的 id 传过来了
                throw new ServiceException("上级文件夹不存在");
            }
            if (depthOf(parent) + 1 >= MAX_DEPTH)
            {
                throw new ServiceException("文件夹最多嵌套 " + MAX_DEPTH + " 层");
            }
        }
        if (folderMapper.countByName(type, parent, name, null) > 0)
        {
            throw new ServiceException("同一层里已经有同名文件夹了");
        }

        SiteInfo site = siteInfoService.require();
        SiteFolder folder = new SiteFolder();
        folder.setFolderId(codeGenerator.nextFolderId());
        folder.setShopCode(site.getShopCode());
        folder.setSiteCode(site.getSiteCode());
        folder.setMediaType(type);
        folder.setParentId(parent);
        folder.setFolderName(name);
        folder.setSort(0);
        folder.setCreateBy(operator);
        folderMapper.insert(folder);
        return folder;
    }

    /** 改名。移动到别的父下走 move()，两件事分开，免得一次请求里两种校验缠在一起 */
    @Transactional(rollbackFor = Exception.class)
    public void rename(String folderId, String folderName, String operator)
    {
        SiteFolder folder = require(folderId);
        String name = requireName(folderName);
        if (folderMapper.countByName(folder.getMediaType(), folder.getParentId(), name, folderId) > 0)
        {
            throw new ServiceException("同一层里已经有同名文件夹了");
        }
        SiteFolder update = new SiteFolder();
        update.setFolderId(folderId);
        update.setFolderName(name);
        update.setUpdateBy(operator);
        folderMapper.update(update);
    }

    /**
     * 挪到另一个父下。
     *
     * <p>⚠ 必须挡住「挪进自己的子孙里」：那会让这一支从树上整个脱落——数据还在，
     * 但 tree() 拼不出来，页面上就是凭空消失。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void move(String folderId, String newParentId, String operator)
    {
        SiteFolder folder = require(folderId);
        String parent = blankToNull(newParentId);

        if (folderId.equals(parent))
        {
            throw new ServiceException("不能把文件夹挪到它自己里面");
        }
        if (parent != null)
        {
            SiteFolder parentFolder = folderMapper.selectById(parent);
            if (parentFolder == null || !folder.getMediaType().equals(parentFolder.getMediaType()))
            {
                throw new ServiceException("目标文件夹不存在");
            }
            if (descendantsOf(folderId).contains(parent))
            {
                throw new ServiceException("不能把文件夹挪进它自己的子文件夹里");
            }
        }

        if (parent == null)
        {
            // ⚠ 挪到顶层要单独一条语句：update 那个动态 SQL 里 parentid 包在
            //   <if test="parentId != null"> 里，传 null 会被整条跳过，表现成「点了没反应」
            folderMapper.moveToRoot(folderId, operator);
        }
        else
        {
            SiteFolder update = new SiteFolder();
            update.setFolderId(folderId);
            update.setParentId(parent);
            update.setUpdateBy(operator);
            folderMapper.update(update);
        }
    }

    /**
     * 删文件夹。
     *
     * <p>里面的文件回落到根（folderid 置 null），子文件夹提升一级，<b>都不删</b>。
     * 理由见类注释。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void remove(String folderId, String operator)
    {
        SiteFolder folder = require(folderId);

        // 子文件夹提到这个文件夹原来的位置上，而不是一律变成顶层——
        // 保住它们原有的从属关系，用户看到的是「少了一层」，不是「全散到根上」
        for (SiteFolder child : folderMapper.selectByType(folder.getMediaType()))
        {
            if (!folderId.equals(child.getParentId()))
            {
                continue;
            }
            if (folder.getParentId() == null)
            {
                folderMapper.moveToRoot(child.getFolderId(), operator);
            }
            else
            {
                SiteFolder update = new SiteFolder();
                update.setFolderId(child.getFolderId());
                update.setParentId(folder.getParentId());
                update.setUpdateBy(operator);
                folderMapper.update(update);
            }
        }

        // 文件回落到根
        if (TYPE_VIDEO.equals(folder.getMediaType()))
        {
            videoMapper.clearFolder(folderId);
        }
        else if (TYPE_MODEL.equals(folder.getMediaType()))
        {
            modelMapper.clearFolder(folderId);
        }
        else
        {
            imageMapper.clearFolder(folderId);
        }

        folderMapper.deleteById(folderId);
    }

    /** 把一批文件挪进某个文件夹（folderId 留空则挪到根）。前端在列表里多选之后调它 */
    @Transactional(rollbackFor = Exception.class)
    public int moveFiles(String mediaType, List<String> fileIds, String folderId)
    {
        String type = normalizeType(mediaType);
        if (fileIds == null || fileIds.isEmpty())
        {
            return 0;
        }
        String target = blankToNull(folderId);
        if (target != null)
        {
            SiteFolder folder = folderMapper.selectById(target);
            if (folder == null || !type.equals(folder.getMediaType()))
            {
                throw new ServiceException("目标文件夹不存在");
            }
        }
        if (TYPE_VIDEO.equals(type))
        {
            return videoMapper.updateFolder(fileIds, target);
        }
        if (TYPE_MODEL.equals(type))
        {
            return modelMapper.updateFolder(fileIds, target);
        }
        return imageMapper.updateFolder(fileIds, target);
    }

    /**
     * 上传时用：文件夹存在且类型对得上才认，否则当成没选（落到根）。
     *
     * <p>不抛异常——上传是个耗时操作，文件都传完了才因为「文件夹刚被别人删了」
     * 整个失败，不如落到根上让用户自己再挪。</p>
     */
    public String resolveUploadFolder(String mediaType, String folderId)
    {
        String target = blankToNull(folderId);
        if (target == null)
        {
            return null;
        }
        SiteFolder folder = folderMapper.selectById(target);
        return folder != null && normalizeType(mediaType).equals(folder.getMediaType()) ? target : null;
    }

    // ==================== 内部 ====================

    private SiteFolder require(String folderId)
    {
        SiteFolder folder = folderId == null ? null : folderMapper.selectById(folderId);
        if (folder == null)
        {
            throw new ServiceException("文件夹不存在");
        }
        return folder;
    }

    /** 认不出来的一律当图片：三个值以外的输入只可能是前端传错，落到图片那棵树上是无害的默认 */
    private String normalizeType(String mediaType)
    {
        if (TYPE_VIDEO.equals(mediaType))
        {
            return TYPE_VIDEO;
        }
        if (TYPE_MODEL.equals(mediaType))
        {
            return TYPE_MODEL;
        }
        return TYPE_IMAGE;
    }

    private String requireName(String folderName)
    {
        String name = folderName == null ? "" : folderName.trim();
        if (name.isEmpty())
        {
            throw new ServiceException("文件夹名不能为空");
        }
        if (name.length() > 50)
        {
            throw new ServiceException("文件夹名不要超过 50 个字");
        }
        // 斜杠会让人以为能用 a/b 一次建两层，实际只是个名字里带斜杠的文件夹
        if (name.contains("/") || name.contains("\\"))
        {
            throw new ServiceException("文件夹名里不能有斜杠");
        }
        return name;
    }

    private static String blankToNull(String value)
    {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    /** 从这个文件夹往上数到顶层有多少层。带环保护：库里真出现环时不至于死循环 */
    private int depthOf(String folderId)
    {
        int depth = 0;
        Set<String> seen = new HashSet<>();
        String current = folderId;
        while (current != null && seen.add(current) && depth < MAX_DEPTH + 1)
        {
            SiteFolder folder = folderMapper.selectById(current);
            if (folder == null)
            {
                break;
            }
            current = folder.getParentId();
            depth++;
        }
        return depth;
    }

    /** 这个文件夹的全部子孙 id。挪动时用来挡「挪进自己的子孙里」 */
    private Set<String> descendantsOf(String folderId)
    {
        SiteFolder self = folderMapper.selectById(folderId);
        Set<String> result = new HashSet<>();
        if (self == null)
        {
            return result;
        }
        List<SiteFolder> all = folderMapper.selectByType(self.getMediaType());
        Set<String> frontier = new HashSet<>();
        frontier.add(folderId);
        // 一层层往下扫，扫不出新的就停。文件夹总量很小，这样比递归查库便宜
        while (!frontier.isEmpty())
        {
            Set<String> next = new HashSet<>();
            for (SiteFolder folder : all)
            {
                if (frontier.contains(folder.getParentId()) && result.add(folder.getFolderId()))
                {
                    next.add(folder.getFolderId());
                }
            }
            frontier = next;
        }
        return result;
    }
}
